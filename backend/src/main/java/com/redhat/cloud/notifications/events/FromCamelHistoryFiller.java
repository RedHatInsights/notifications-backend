package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.Json;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;

/**
 * We sent data via Camel. Now Camel informs us about the outcome,
 * which we need to put into the notifications history.
 */
@ApplicationScoped
public class FromCamelHistoryFiller {

    public static final String FROMCAMEL_CHANNEL = "fromCamel";
    public static final String MESSAGES_ERROR_COUNTER_NAME = "camel.messages.error";
    public static final String MESSAGES_PROCESSED_COUNTER_NAME = "camel.messages.processed";

    private static final Logger log = Logger.getLogger(FromCamelHistoryFiller.class);

    @Inject
    NotificationHistoryRepository notificationHistoryRepository;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Inject
    MeterRegistry meterRegistry;

    private Counter messagesProcessedCounter;
    private Counter messagesErrorCounter;

    @PostConstruct
    void init() {
        messagesProcessedCounter = meterRegistry.counter(MESSAGES_PROCESSED_COUNTER_NAME);
        messagesErrorCounter = meterRegistry.counter(MESSAGES_ERROR_COUNTER_NAME);
    }

    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    @Incoming(FROMCAMEL_CHANNEL)
    // Can be modified to use Multi<Message<String>> input also for more concurrency
    public Uni<Void> processAsync(Message<String> input) {
        return Uni.createFrom().item(() -> input.getPayload())
                .onItem().invoke(payload -> log.infof("Processing return from camel: %s", payload))
                .onItem().transform(this::decodeItem)
                .onItem()
                .transformToUni(payload -> {
                    return sessionFactory.withStatelessSession(statelessSession -> {
                        return notificationHistoryRepository.updateHistoryItem(payload)
                                .onFailure().invoke(t -> log.info("|  Update Fail", t)
                                );
                    });
                })
                .onFailure().recoverWithUni(t -> {
                    messagesErrorCounter.increment();
                    log.error("|  Failure to update the history", t);
                    return Uni.createFrom().voidItem();
                })
                .eventually(() -> messagesProcessedCounter.increment());
    }

    private Map<String, Object> decodeItem(String s) {

        // 1st step CloudEvent as String -> map
        Map<String, Object> ceMap = Json.decodeValue(s, Map.class);

        // Take the id from the CloudEvent as the historyId
        String id = (String) ceMap.get("id");

        // 2nd step data item (as String) to final map
        Map<String, Object> map = Json.decodeValue((String) ceMap.get("data"), Map.class);
        map.put("historyId", id);
        return map;
    }

}
