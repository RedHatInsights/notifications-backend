package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.EventResources;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.utils.ActionParser;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;

import java.util.UUID;

import static org.eclipse.microprofile.reactive.messaging.Acknowledgment.Strategy.PRE_PROCESSING;

@ApplicationScoped
public class EventConsumer {

    public static final String INGRESS_CHANNEL = "ingress";
    public static final String REJECTED_COUNTER_NAME = "input.rejected";
    public static final String PROCESSING_ERROR_COUNTER_NAME = "input.processing.error";
    public static final String DUPLICATE_COUNTER_NAME = "input.duplicate";
    public static final String CONSUMED_TIMER_NAME = "input.consumed";

    private static final Logger LOGGER = Logger.getLogger(EventConsumer.class);
    private static final String EVENT_TYPE_NOT_FOUND_MSG = "No event type found for [bundleName=%s, applicationName=%s, eventTypeName=%s]";

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

    @Inject
    Mutiny.SessionFactory sessionFactory;

    private Counter rejectedCounter;
    private Counter processingErrorCounter;
    private Counter duplicateCounter;

    @PostConstruct
    public void init() {
        rejectedCounter = registry.counter(REJECTED_COUNTER_NAME);
        processingErrorCounter = registry.counter(PROCESSING_ERROR_COUNTER_NAME);
        duplicateCounter = registry.counter(DUPLICATE_COUNTER_NAME);
    }

    @Incoming(INGRESS_CHANNEL)
    @Acknowledgment(PRE_PROCESSING)
    public Uni<Void> processAsync(Message<String> message) {
        // This timer will have dynamic tag values based on the action parsed from the received message.
        Timer.Sample consumedTimer = Timer.start(registry);
        String payload = message.getPayload();
        // The two following variables have to be final or effectively final. That why their type is String[] instead of String.
        String[] bundleName = new String[1];
        String[] appName = new String[1];
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
                    bundleName[0] = action.getBundle();
                    appName[0] = action.getApplication();
                    String eventTypeName = action.getEventType();
                    LOGGER.infof("Processing received action: (%s) %s/%s/%s", action.getAccountId(), bundleName[0], appName[0], eventTypeName);
                    /*
                     * Step 2
                     * The message ID is extracted from the Kafka message headers. It can be null for now to give the
                     * onboarded apps time to change their integration and start sending the new header. The message ID
                     * may become mandatory later. If so, we may want to throw an exception when it is null.
                     */
                    UUID messageId = kafkaMessageDeduplicator.findMessageId(bundleName[0], appName[0], message);
                    return sessionFactory.withStatelessSession(statelessSession -> {
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
                                        return appResources.getEventType(bundleName[0], appName[0], eventTypeName)
                                                .onFailure(NoResultException.class).transform(e ->
                                                        new NoResultException(String.format(EVENT_TYPE_NOT_FOUND_MSG, bundleName[0], appName[0], eventTypeName))
                                                )
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
                    });
                })
                .onItemOrFailure().transformToUni((unused, throwable) -> {
                    if (throwable != null) {
                        /*
                         * An exception was thrown at some point during the Kafka message processing, it is logged.
                         */
                        LOGGER.infof(throwable, "Could not process the payload: %s", payload);
                    }
                    // bundleName[0] and appName[0] are null when the action parsing failed.
                    String bundle = bundleName[0] == null ? "" : bundleName[0];
                    String application = appName[0] == null ? "" : appName[0];
                    consumedTimer.stop(registry.timer(CONSUMED_TIMER_NAME, "bundle", bundle, "application", application));
                    return Uni.createFrom().voidItem();
                });
    }
}
