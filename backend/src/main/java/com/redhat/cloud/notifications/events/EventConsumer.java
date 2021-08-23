package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.utils.ActionParser;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment.Strategy;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class EventConsumer {

    public static final String INGRESS_CHANNEL = "ingress";
    public static final String REJECTED_COUNTER_NAME = "input.rejected";
    public static final String PROCESSING_ERROR_COUNTER_NAME = "input.processing.error";

    private static final Logger LOGGER = Logger.getLogger(EventConsumer.class);

    @Inject
    MeterRegistry registry;

    @Inject
    EndpointProcessor endpointProcessor;

    @Inject
    ActionParser actionParser;

    @Inject
    ApplicationResources appResources;

    private Counter rejectedCount;
    private Counter processingErrorCount;

    @PostConstruct
    public void init() {
        rejectedCount = registry.counter(REJECTED_COUNTER_NAME);
        processingErrorCount = registry.counter(PROCESSING_ERROR_COUNTER_NAME);
    }

    @Incoming(INGRESS_CHANNEL)
    @Acknowledgment(Strategy.PRE_PROCESSING)
    // Can be modified to use Multi<Message<String>> input also for more concurrency
    public Uni<Void> processAsync(Message<String> input) {
        String payload = input.getPayload();
        /*
         * Step 1
         * The payload JSON is parsed into an Action. It can throw an UncheckedIOException if the parsing fails.
         */
        return actionParser.fromJsonString(payload)
                .onItem().invoke(action -> LOGGER.infof("Processing received action: (%s) %s/%s/%s",
                        action.getAccountId(), action.getBundle(), action.getApplication(), action.getEventType())
                )
                /*
                 * Step 2
                 * The payload JSON was successfully parsed. The resulting Action contains a bundle/app/eventType triplet
                 * which is used to retrieve an EventType from the DB. If the event type is found, an Event is created
                 * and persisted. Otherwise, a NoResultException is thrown.
                 */
                .onItem().transformToUni(action -> appResources.getEventType(action.getBundle(), action.getApplication(), action.getEventType())
                        .onItem().transformToUni(eventType -> {
                            Event event = new Event(eventType, payload, action);
                            return appResources.createEvent(event);
                        })
                )
                // If an exception was thrown during steps 1 or 2, the payload is considered rejected.
                .onFailure().invoke(() -> rejectedCount.increment())
                /*
                 * Step 3
                 * The Event and the Action it contains are processed by all relevant endpoint processors.
                 */
                .onItem().transformToUni(event -> endpointProcessor.process(event)
                        .onFailure().invoke(() -> processingErrorCount.increment())
                )
                .onItemOrFailure().transformToUni((unused, throwable) -> {
                    // If an exception was thrown during steps 1, 2 or 3, an INFO entry with the payload is added to the log.
                    if (throwable != null) {
                        LOGGER.infof(throwable, "Could not process the payload: %s", payload);
                    }
                    return Uni.createFrom().voidItem();
                });
    }

}
