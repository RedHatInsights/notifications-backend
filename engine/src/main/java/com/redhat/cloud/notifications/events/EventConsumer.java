package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.cloudevent.transformers.CloudEventTransformer;
import com.redhat.cloud.notifications.cloudevent.transformers.CloudEventTransformerFactory;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.db.repositories.EventTypeRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.utils.ActionParser;
import com.redhat.cloud.notifications.utils.CloudEventParser;
import com.redhat.cloud.notifications.utils.ParsingException;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    static final String TAG_KEY_BUNDLE = "bundle";
    static final String TAG_KEY_APPLICATION = "application";
    static final String TAG_KEY_EVENT_TYPE_FQN = "event-type-fqn";

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

    @Inject
    CloudEventTransformerFactory cloudEventTransformerFactory;

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
        Map<String, String> tags = new HashMap<>();
        // The two following variables have to be final or effectively final. That why their type is String[] instead of String.
        /*
         * Step 1
         * The payload (JSON) is parsed into an Action.
         */
        try {

            final EventWrapper<?, ?> eventWrapper = parsePayload(payload, tags);
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
            final UUID messageId = getMessageId(eventWrapper, message);

            String msgId = messageId == null ? "null" : messageId.toString();
            Log.infof("Processing received event [id=%s, %s=%s, orgId=%s, %s]",
                    eventWrapper.getId(), MESSAGE_ID_HEADER, msgId, eventWrapper.getOrgId(), eventWrapper.getKey());

            statelessSessionFactory.withSession(statelessSession -> {
                /*
                 * Step 3
                 * It's time to check if the message ID is already known. For now, messages without an ID
                 * (messageId == null) are always considered new.
                 */
                if (kafkaMessageDeduplicator.isDuplicate(messageId)) {
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
                    kafkaMessageDeduplicator.registerMessageId(messageId);
                    /*
                     * Step 5
                     * We need to retrieve an EventType from the DB using the bundle/app/eventType triplet from the
                     * parsed Action.
                     */
                    EventType eventType;
                    EventWrapper<?, ?> eventWrapperToProcess = eventWrapper;
                    try {
                        eventType = eventTypeRepository.getEventType(eventWrapperToProcess.getKey());

                        if (eventWrapperToProcess instanceof EventWrapperCloudEvent) {
                            // We loaded a cloud event and identified the event-type it belongs to
                            // At this point, lets check if we have a transformation available for this event
                            // If we do, transform the event - Later this will be done on a by-integration basis
                            Optional<CloudEventTransformer> transformer = cloudEventTransformerFactory.getTransformerIfSupported((EventWrapperCloudEvent) eventWrapperToProcess);
                            if (transformer.isPresent()) {
                                eventWrapperToProcess = new EventWrapperAction(
                                        transformer.get().toAction(
                                                (EventWrapperCloudEvent) eventWrapperToProcess,
                                                eventType.getApplication().getBundle().getName(),
                                                eventType.getApplication().getName(),
                                                eventType.getName()
                                ));
                            }
                        }

                        tags.computeIfAbsent(TAG_KEY_BUNDLE, key -> eventType.getApplication().getBundle().getName());
                        tags.computeIfAbsent(TAG_KEY_APPLICATION, key -> eventType.getApplication().getName());
                    } catch (NoResultException | IllegalArgumentException e) {
                        /*
                         * A NoResultException was thrown because no EventType was found. The message is therefore
                         * considered rejected.
                         */
                        rejectedCounter.increment();
                        throw new NoResultException(String.format(EVENT_TYPE_NOT_FOUND_MSG, eventWrapperToProcess.getKey()));
                    }
                    /*
                     * Step 6
                     * The EventType was found. It's time to create an Event from the current message and persist it.
                     */
                    Event event = new Event(eventType, payload, eventWrapperToProcess);
                    if (event.getId() == null) {
                        // NOTIF-499 If there is no ID provided whatsoever we create one.
                        event.setId(Objects.requireNonNullElseGet(messageId, UUID::randomUUID));
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
            consumedTimer.stop(registry.timer(
                    CONSUMED_TIMER_NAME,
                    TAG_KEY_BUNDLE, tags.getOrDefault(TAG_KEY_BUNDLE, ""),
                    TAG_KEY_APPLICATION, tags.getOrDefault(TAG_KEY_APPLICATION, ""),
                    TAG_KEY_EVENT_TYPE_FQN, tags.getOrDefault(TAG_KEY_EVENT_TYPE_FQN, "")
            ));
        }
        return message.ack();
    }

    private EventWrapper<?, ?> parsePayload(String payload, Map<String, String> tags) {
        try {
            Action action = actionParser.fromJsonString(payload);
            tags.putIfAbsent(TAG_KEY_BUNDLE, action.getBundle());
            tags.putIfAbsent(TAG_KEY_APPLICATION, action.getApplication());
            return new EventWrapperAction(action);
        } catch (ParsingException actionParseException) {
            // Try to load it as a CloudEvent
            try {
                EventWrapperCloudEvent eventWrapperCloudEvent = new EventWrapperCloudEvent(cloudEventParser.fromJsonString(payload));
                tags.putIfAbsent(TAG_KEY_EVENT_TYPE_FQN, eventWrapperCloudEvent.getKey().getFullyQualifiedName());
                return eventWrapperCloudEvent;
            } catch (ParsingException cloudEventParseException) {
                /*
                 * An exception (most likely UncheckedIOException) was thrown during the payload parsing. The message
                 * is therefore considered rejected.
                 */
                rejectedCounter.increment();

                actionParseException.addSuppressed(cloudEventParseException);
                throw actionParseException;
            }
        }
    }

    private UUID getMessageId(EventWrapper<?, ?> eventWrapper, Message<String> message) {
        UUID messageId = eventWrapper.getId();
        if (messageId == null) {
            messageId = kafkaMessageDeduplicator.findMessageId(eventWrapper.getKey(), message);
        }

        return messageId;
    }
}
