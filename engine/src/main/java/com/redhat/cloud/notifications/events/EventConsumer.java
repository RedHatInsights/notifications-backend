package com.redhat.cloud.notifications.events;

import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.event.parser.exceptions.ConsoleCloudEventParsingException;
import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.cloudevent.transformers.CloudEventTransformer;
import com.redhat.cloud.notifications.cloudevent.transformers.CloudEventTransformerFactory;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.db.repositories.EventTypeRepository;
import com.redhat.cloud.notifications.events.deduplication.EventDeduplicator;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationsConsoleCloudEvent;
import com.redhat.cloud.notifications.transformers.SeverityTransformer;
import com.redhat.cloud.notifications.utils.ActionParser;
import com.redhat.cloud.notifications.utils.ActionParsingException;
import com.redhat.cloud.notifications.utils.RecipientsAuthorizationCriterionExtractor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_HEADER;
import static java.util.concurrent.TimeUnit.SECONDS;

@ApplicationScoped
public class EventConsumer {

    public static final String INGRESS_CHANNEL = "ingress";
    public static final String INGRESS_REPLAY_CHANNEL = "ingressreplay";
    public static final String REJECTED_COUNTER_NAME = "input.rejected";
    public static final String PROCESSING_ERROR_COUNTER_NAME = "input.processing.error";
    public static final String PROCESSING_BLACKLISTED_COUNTER_NAME = "input.processing.blacklisted";
    public static final String PROCESSING_EXCEPTION_COUNTER_NAME = "input.processing.exception";
    public static final String DUPLICATE_EVENT_COUNTER_NAME = "input.duplicate.event";
    public static final String CONSUMED_TIMER_NAME = "input.consumed";
    public static final String REPLAYED_MESSAGE_COUNTER_NAME = "input.processing.replayed";

    static final String TAG_KEY_BUNDLE = "bundle";
    static final String TAG_KEY_APPLICATION = "application";
    static final String TAG_KEY_EVENT_TYPE = "event-type";
    static final String TAG_KEY_EVENT_TYPE_FQN = "event-type-fqn";

    private static final String EVENT_TYPE_NOT_FOUND_MSG = "No event type found for key: %s";
    private static final String SOURCE_ENVIRONMENT_HEADER = "rh-source-environment";

    @Inject
    MeterRegistry registry;

    @Inject
    EndpointProcessor endpointProcessor;

    @Inject
    ActionParser actionParser;

    @Inject
    EventTypeRepository eventTypeRepository;

    @Inject
    EventRepository eventRepository;

    @Inject
    KafkaMessageDeduplicator kafkaMessageDeduplicator;

    @Inject
    EventDeduplicator eventDeduplicator;

    @Inject
    EngineConfig engineConfig;

    @Inject
    CloudEventTransformerFactory cloudEventTransformerFactory;

    @Inject
    KafkaHeadersExtractor kafkaHeadersExtractor;

    @Inject
    EngineConfig config;

    @Inject
    RecipientsAuthorizationCriterionExtractor recipientsAuthorizationCriterionExtractor;

    @Inject
    SeverityTransformer severityTransformer;

    ConsoleCloudEventParser cloudEventParser = new ConsoleCloudEventParser();

    private Counter rejectedCounter;
    private Counter processingErrorCounter;
    private Counter processingExceptionCounter;
    private ExecutorService executor;

    Instant startTime = Instant.parse("2026-02-10T09:02:00Z");
    Instant endTime = Instant.parse("2026-02-10T09:22:00Z");
    private Counter replayedMessageCounter;

    @PostConstruct
    public void init() {
        rejectedCounter = registry.counter(REJECTED_COUNTER_NAME);
        processingErrorCounter = registry.counter(PROCESSING_ERROR_COUNTER_NAME);
        processingExceptionCounter = registry.counter(PROCESSING_EXCEPTION_COUNTER_NAME);
        replayedMessageCounter = registry.counter(REPLAYED_MESSAGE_COUNTER_NAME);

        /*
         * The ThreadPoolExecutor#submit method from this executor is blocking. If it is called while all threads from
         * the pool are busy, the calling thread will wait until a thread from the pool is available.
         */
        // TODO After we've confirmed the async processing works, additional code will be needed to shutdown the executor gracefully (wait until all threads are done with their work).
        executor = new ThreadPoolExecutor(
                config.getEventConsumerCoreThreadPoolSize(),
                config.getEventConsumerMaxThreadPoolSize(),
                config.getEventConsumerKeepAliveTimeSeconds(),
                SECONDS,
                buildBlockingQueue()
        );
    }

    private BlockingQueue<Runnable> buildBlockingQueue() {
        return new LinkedBlockingQueue<>(config.getEventConsumerQueueCapacity()) {
            @Override
            public boolean offer(Runnable runnable) {
                try {
                    /*
                     * The ThreadPoolExecutor internal implementation can only insert elements in the queue by calling
                     * the BlockingQueue#offer method, which is not a blocking method. Since we need a blocking behavior
                     * to prevent Kafka messages from being consumed when all threads are busy, the call is delegated to
                     * the BlockingQueue#put method.
                     */
                    put(runnable);
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        };
    }

    @Incoming(INGRESS_REPLAY_CHANNEL)
    @Blocking
    public CompletionStage<Void> consumeReplay(Message<String> message) throws InterruptedException {

        if (config.isSkipMessageProcessing()) {
            return message.ack();
        }

        if (config.isAddDelayOnReplayService()) {
            Thread.sleep(1000);
        }
        Optional<KafkaMessageMetadata> metadata = message.getMetadata(KafkaMessageMetadata.class);
        if (metadata.isPresent()) {
            Instant kafkaTimestamp = metadata.get().getTimestamp();
            if (kafkaTimestamp != null && kafkaTimestamp.isAfter(startTime) && kafkaTimestamp.isBefore(endTime)) {
                process(message);
                replayedMessageCounter.increment();
            }
        }
        return message.ack();
    }

    @Incoming(INGRESS_CHANNEL)
    @Blocking
    public CompletionStage<Void> consume(Message<String> message) {
        if (config.isAsyncEventProcessing()) {
            /*
             * Even though the processing will be asynchronous, this call will block the current thread until a thread
             * from the pool is available.
             */
            executor.submit(() -> process(message));
        } else {
            process(message);
        }
        return message.ack();
    }

    @ActivateRequestContext
    public void process(Message<String> message) {
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
             * The event data was successfully parsed (either as an action or a cloud event). Depending on the situation
             * we now have a bundle/app/eventType triplet or a fully qualified name for the event type.
             */

            Map<String, Optional<String>> kafkaHeaders = kafkaHeadersExtractor.extract(message,
                    MESSAGE_ID_HEADER,
                    SOURCE_ENVIRONMENT_HEADER
            );

            /*
             * Step 2
             * The message ID is extracted from the event data - if it is not present we fallback to the kafka headers
             * It can be null for now to give the onboarded
             * apps time to change their integration and start sending the new header. The message ID will become
             * mandatory with cloud events. We may want to throw an exception when it is null.
             */
            final UUID messageId = getMessageId(eventWrapper, kafkaHeaders.get(MESSAGE_ID_HEADER));

            String msgId = messageId == null ? "null" : messageId.toString();
            Log.infof("Processing received event [id=%s, %s=%s, orgId=%s, %s]",
                    eventWrapper.getId(), MESSAGE_ID_HEADER, msgId, eventWrapper.getOrgId(), eventWrapper.getKey());

            /*
             * Step 3
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
                tags.computeIfAbsent(TAG_KEY_EVENT_TYPE, key -> eventType.getName());

                if (config.isBlacklistedEventType(eventType.getId())) {
                    Log.debugf("Skipping event type [id=%s, name=%s] because it was blacklisted", eventType.getId(), eventType.getName());
                    registry.counter(
                        PROCESSING_BLACKLISTED_COUNTER_NAME,
                        TAG_KEY_BUNDLE, tags.getOrDefault(TAG_KEY_BUNDLE, ""),
                        TAG_KEY_APPLICATION, tags.getOrDefault(TAG_KEY_APPLICATION, ""),
                        TAG_KEY_EVENT_TYPE, tags.getOrDefault(TAG_KEY_EVENT_TYPE, ""),
                        TAG_KEY_EVENT_TYPE_FQN, tags.getOrDefault(TAG_KEY_EVENT_TYPE_FQN, ""))
                        .increment();
                    return;
                }
            } catch (NoResultException | IllegalArgumentException e) {
                /*
                 * A NoResultException was thrown because no EventType was found. The message is therefore
                 * considered rejected.
                 */
                rejectedCounter.increment();
                throw new NoResultException(String.format(EVENT_TYPE_NOT_FOUND_MSG, eventWrapperToProcess.getKey()));
            }
            /*
             * Step 4
             * The EventType was found. It's time to create an Event from the current message.
             */
            Optional<String> sourceEnvironmentHeader = kafkaHeaders.get(SOURCE_ENVIRONMENT_HEADER);
            Event event = new Event(eventType, payload, eventWrapperToProcess, sourceEnvironmentHeader, messageId);

            /*
             * Step 5
             * Before we persist the event into the DB and process it, we need to check whether the event is
             * a duplicate using the custom event deduplication logic tenants might have implemented.
             */
            if (!eventDeduplicator.isNew(event)) {
                // The event is already known and should therefore be ignored.
                Log.debug("Duplicated event ignored");
                registry.counter(DUPLICATE_EVENT_COUNTER_NAME,
                    TAG_KEY_BUNDLE, tags.getOrDefault(TAG_KEY_BUNDLE, ""),
                    TAG_KEY_APPLICATION, tags.getOrDefault(TAG_KEY_APPLICATION, ""),
                    TAG_KEY_EVENT_TYPE, tags.getOrDefault(TAG_KEY_EVENT_TYPE, ""))
                    .increment();
            } else {

                /*
                 * Step 6
                 * The event is not a duplicate. We can now persist it.
                 */
                event.setHasAuthorizationCriterion(null != recipientsAuthorizationCriterionExtractor.extract(event));
                updateSeverity(event);

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
                    TAG_KEY_EVENT_TYPE, tags.getOrDefault(TAG_KEY_EVENT_TYPE, ""),
                    TAG_KEY_EVENT_TYPE_FQN, tags.getOrDefault(TAG_KEY_EVENT_TYPE_FQN, "")
            ));
        }
    }

    private void updateSeverity(Event event) {
        final Severity severity = severityTransformer.getSeverity(event);
        event.setSeverity(severity);

        if (event.getEventWrapper() instanceof EventWrapperAction evtAction) {
            if (severity != null) {
                evtAction.getEvent().setSeverity(severity.name());
            }
        }
    }

    private EventWrapper<?, ?> parsePayload(String payload, Map<String, String> tags) {
        try {
            Action action = actionParser.fromJsonString(payload);
            tags.put(TAG_KEY_BUNDLE, action.getBundle());
            tags.put(TAG_KEY_APPLICATION, action.getApplication());
            tags.put(TAG_KEY_EVENT_TYPE, action.getEventType());
            return new EventWrapperAction(action);
        } catch (ActionParsingException actionParseException) {
            // Try to load it as a CloudEvent
            try {
                EventWrapperCloudEvent eventWrapperCloudEvent = new EventWrapperCloudEvent(cloudEventParser.fromJsonString(payload, NotificationsConsoleCloudEvent.class));
                tags.put(TAG_KEY_EVENT_TYPE_FQN, eventWrapperCloudEvent.getKey().getFullyQualifiedName());
                return eventWrapperCloudEvent;
            } catch (ConsoleCloudEventParsingException cloudEventParseException) {
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

    private UUID getMessageId(EventWrapper<?, ?> eventWrapper, Optional<String> messageIdHeader) {
        UUID messageId = eventWrapper.getId();
        if (messageId == null) {
            messageId = kafkaMessageDeduplicator.validateMessageId(eventWrapper.getKey(), messageIdHeader);
        }

        return messageId;
    }
}
