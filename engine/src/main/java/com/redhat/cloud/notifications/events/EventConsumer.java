package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.db.repositories.EventTypeRepository;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.utils.ActionParser;
import com.redhat.cloud.notifications.utils.CloudEventParser;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.annotations.Blocking;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.inject.Inject;
import javax.persistence.NoResultException;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_HEADER;
import static org.eclipse.microprofile.reactive.messaging.Acknowledgment.Strategy.PRE_PROCESSING;

@ApplicationScoped
public class EventConsumer {

    public static final String INGRESS_CHANNEL = "ingress";
    public static final String REJECTED_COUNTER_NAME = "input.rejected";
    public static final String PROCESSING_ERROR_COUNTER_NAME = "input.processing.error";
    public static final String PROCESSING_EXCEPTION_COUNTER_NAME = "input.processing.exception";
    public static final String DUPLICATE_COUNTER_NAME = "input.duplicate";
    public static final String CONSUMED_TIMER_NAME = "input.consumed";

    private static final String EVENT_TYPE_NOT_FOUND_MSG = "No event type found for key: %s";

    @Inject
    MeterRegistry registry;

    @Inject
    EndpointProcessor endpointProcessor;

    @Inject
    ActionParser actionParser;

    @Inject
    CloudEventParser cloudEventParser;

    @Inject
    EventTypeRepository eventTypeRepository;

    @Inject
    EventRepository eventRepository;

    @Inject
    KafkaMessageDeduplicator kafkaMessageDeduplicator;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    private Counter rejectedCounter;
    private Counter processingErrorCounter;
    private Counter duplicateCounter;
    private Counter processingExceptionCounter;

    @PostConstruct
    public void init() {
        rejectedCounter = registry.counter(REJECTED_COUNTER_NAME);
        processingErrorCounter = registry.counter(PROCESSING_ERROR_COUNTER_NAME);
        processingExceptionCounter = registry.counter(PROCESSING_EXCEPTION_COUNTER_NAME);
        duplicateCounter = registry.counter(DUPLICATE_COUNTER_NAME);
    }

    @Incoming(INGRESS_CHANNEL)
    @Acknowledgment(PRE_PROCESSING)
    @Blocking
    @ActivateRequestContext
    public CompletionStage<Void> process(Message<String> message) {
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
        try {
            EventData<?> eventData;
            try {
                eventData = new EventDataAction(actionParser.fromJsonString(payload));
            } catch (Exception actionParseException) {
                // Try to load it as a CloudEvent
                try {
                    eventData = new EventDataCloudEvent(cloudEventParser.fromJsonString(payload));
                } catch (Exception cloudEventParseException) {
                    /*
                     * An exception (most likely UncheckedIOException) was thrown during the payload parsing. The message
                     * is therefore considered rejected.
                     */
                    rejectedCounter.increment();

                    actionParseException.addSuppressed(cloudEventParseException);
                    throw actionParseException;
                }
            }

            /*
             * The event data was successfully parsed (either as an action or a cloud event). Depending the situation
             * we now have a bundle/app/eventType triplet or a fully qualified name for the event type.
             */

            /*
             * Step 2
             * The message ID is extracted from the event data - if it is not present we fallback to the kafka headers
             * It can be null for now to give the onboarded
             * apps time to change their integration and start sending the new header. The message ID will become
             * mandatory with cloud events. We may want to throw an exception when it is null.
             */
            UUID messageId = eventData.getId();
            if (messageId == null) {
                messageId = kafkaMessageDeduplicator.findMessageId(bundleName[0], appName[0], message);
            }

            String msgId = messageId == null ? "null" : messageId.toString();
            Log.infof("Processing received event [id=%s, %s=%s, orgId=%s, %s]",
                    eventData.getId(), MESSAGE_ID_HEADER, msgId, eventData.getOrgId(), eventData.getEventTypeKey());

            final UUID finalMessageId = messageId;
            final EventData<?> finalEventData = eventData;
            statelessSessionFactory.withSession(statelessSession -> {
                /*
                 * Step 3
                 * It's time to check if the message ID is already known. For now, messages without an ID
                 * (messageId == null) are always considered new.
                 */
                if (kafkaMessageDeduplicator.isDuplicate(finalMessageId)) {
                    /*
                     * The message ID is already known which means we already processed the current
                     * message and sent notifications. The message is therefore ignored.
                     */
                    duplicateCounter.increment();
                } else {
                    /*
                     * Step 4
                     * The message ID is new. Let's persist it. The current message will never be processed again as
                     * long as its ID stays in the DB.
                     */
                    kafkaMessageDeduplicator.registerMessageId(finalMessageId);
                    /*
                     * Step 5
                     * We need to retrieve an EventType from the DB using the bundle/app/eventType triplet from the
                     * parsed Action.
                     */
                    EventType eventType;
                    try {
                        eventType = eventTypeRepository.getEventType(finalEventData.getEventTypeKey());
                    } catch (NoResultException e) {
                        /*
                         * A NoResultException was thrown because no EventType was found. The message is therefore
                         * considered rejected.
                         */
                        rejectedCounter.increment();
                        throw new NoResultException(String.format(EVENT_TYPE_NOT_FOUND_MSG, finalEventData.getEventTypeKey()));
                    }
                    /*
                     * Step 6
                     * The EventType was found. It's time to create an Event from the current message and persist it.
                     */
                    Event event = new Event(eventType, payload, finalEventData);
                    if (event.getId() == null) {
                        // NOTIF-499 If there is no ID provided whatsoever we create one.
                        event.setId(Objects.requireNonNullElseGet(finalMessageId, UUID::randomUUID));
                    }
                    eventRepository.create(event);
                    /*
                     * Step 7
                     * The Event and the Action it contains are processed by all relevant endpoint processors.
                     */
                    try {
                        endpointProcessor.process(event);
                    } catch (Exception e) {
                        /*
                         * The Event processing failed.
                         */
                        processingErrorCounter.increment();
                        throw e;
                    }
                }
            });
        } catch (Exception e) {
            /*
             * An exception was thrown at some point during the Kafka message processing,
             * it is logged and added to the exception counter metric.
             */
            processingExceptionCounter.increment();
            Log.infof(e, "Could not process the payload: %s", payload);
        } finally {
            // bundleName[0] and appName[0] are null when the action parsing failed.
            String bundle = bundleName[0] == null ? "" : bundleName[0];
            String application = appName[0] == null ? "" : appName[0];
            consumedTimer.stop(registry.timer(CONSUMED_TIMER_NAME, "bundle", bundle, "application", application));
        }
        return message.ack();
    }
}
