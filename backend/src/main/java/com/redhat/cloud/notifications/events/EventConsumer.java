package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.EventResources;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.utils.ActionParser;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.util.UUID;

import static org.eclipse.microprofile.reactive.messaging.Acknowledgment.Strategy.PRE_PROCESSING;

@ApplicationScoped
public class EventConsumer {

    public static final String INGRESS_CHANNEL = "ingress";
    public static final String REJECTED_COUNTER_NAME = "input.rejected";
    public static final String PROCESSING_ERROR_COUNTER_NAME = "input.processing.error";
    public static final String DUPLICATE_COUNTER_NAME = "input.duplicate";
    public static final String CONSUMED_COUNTER_NAME = "input.consumed";

    private static final Logger LOGGER = Logger.getLogger(EventConsumer.class);

    @Inject
    MeterRegistry registry;

    @Inject
    EndpointProcessor endpointProcessor;

    @Inject
    ActionParser actionParser;

    @Inject
    ApplicationResources appResources;

    @Inject
    EventResources eventResources;

    @Inject
    KafkaMessageDeduplicator kafkaMessageDeduplicator;

    private Counter rejectedCounter;
    private Counter processingErrorCounter;
    private Counter duplicateCounter;
    private Counter consumedCounter;

    @PostConstruct
    public void init() {
        rejectedCounter = registry.counter(REJECTED_COUNTER_NAME);
        processingErrorCounter = registry.counter(PROCESSING_ERROR_COUNTER_NAME);
        duplicateCounter = registry.counter(DUPLICATE_COUNTER_NAME);
        consumedCounter = registry.counter(CONSUMED_COUNTER_NAME);
    }

    @Incoming(INGRESS_CHANNEL)
    @Acknowledgment(PRE_PROCESSING)
    public Uni<Void> processAsync(Message<String> message) {
        String payload = message.getPayload();
        /*
         * Step 1
         * The payload (JSON) is parsed into an Action.
         */
        return actionParser.fromJsonString(payload)
                .onFailure().invoke(() -> {
                    /*
                     * An exception (most likely UncheckedIOException) was thrown during the payload parsing. The message
                     * is therefore considered rejected.
                     */
                    rejectedCounter.increment();
                })
                .onItem().transformToUni(action -> {
                    /*
                     * The payload was successfully parsed. The resulting Action contains a bundle/app/eventType triplet
                     * which is logged.
                     */
                    String bundleName = action.getBundle();
                    String appName = action.getApplication();
                    String eventTypeName = action.getEventType();
                    LOGGER.infof("Processing received action: (%s) %s/%s/%s", action.getAccountId(), bundleName, appName, eventTypeName);
                    /*
                     * Step 2
                     * The message ID is extracted from the Kafka message headers. It can be null for now to give the
                     * onboarded apps time to change their integration and start sending the new header. The message ID
                     * may become mandatory later. If so, we may want to throw an exception when it is null.
                     */
                    UUID messageId = kafkaMessageDeduplicator.findMessageId(bundleName, appName, message);
                    /*
                     * Step 3
                     * It's time to check if the message ID is already known. For now, messages without an ID
                     * (messageId == null) are always considered new.
                     */
                    return kafkaMessageDeduplicator.isDuplicate(messageId)
                            .onItem().transformToUni(isDuplicate -> {
                                if (isDuplicate) {
                                    /*
                                     * The message ID is already known which means we already processed the current
                                     * message and sent notifications. The message is therefore ignored.
                                     */
                                    duplicateCounter.increment();
                                    return Uni.createFrom().voidItem();
                                } else {
                                    /*
                                     * Step 4
                                     * The message ID is new. We need to retrieve an EventType from the DB using the
                                     * bundle/app/eventType triplet from the parsed Action.
                                     */
                                    return appResources.getEventType(bundleName, appName, eventTypeName)
                                            .onFailure().invoke(() -> {
                                                /*
                                                 * A NoResultException was thrown because no EventType was found. The
                                                 * message is therefore considered rejected.
                                                 */
                                                rejectedCounter.increment();
                                            })
                                            .onItem().transformToUni(eventType -> {
                                                /*
                                                 * Step 5
                                                 * The EventType was found. It's time to create an Event from the current
                                                 * message and persist it.
                                                 */
                                                Event event = new Event(eventType, payload, action);
                                                return eventResources.create(event);
                                            })
                                            /*
                                             * Step 6
                                             * The Event and the Action it contains are processed by all relevant endpoint
                                             * processors.
                                             */
                                            .onItem().transformToUni(event -> endpointProcessor.process(event)
                                                    .onFailure().invoke(() -> {
                                                        /*
                                                         * The Event processing failed.
                                                         */
                                                        processingErrorCounter.increment();
                                                    })
                                            )
                                            .eventually(() -> {
                                                /*
                                                 * Step 7
                                                 * The Kafka message processing is done and its ID is persisted no matter
                                                 * what the processing outcome is (success or failure). That message ID
                                                 * will never be processed again as long as it stays in the DB.
                                                 */
                                                return kafkaMessageDeduplicator.registerMessageId(messageId);
                                            });
                                }
                            });
                })
                .onItemOrFailure().transformToUni((unused, throwable) -> {
                    if (throwable != null) {
                        /*
                         * An exception was thrown at some point during the Kafka message processing, it is logged.
                         */
                        LOGGER.infof(throwable, "Could not process the payload: %s", payload);
                    }
                    /*
                     * This counter main purpose is to prevent a test race condition.
                     */
                    consumedCounter.increment();
                    return Uni.createFrom().voidItem();
                });
    }
}
