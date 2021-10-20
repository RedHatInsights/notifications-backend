package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.NotificationResources;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.Json;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;
import java.util.logging.Logger;

/**
 * We sent data via Camel. Now Camel informs us about the outcome,
 * which we need to put into the notifications history.
 */
@ApplicationScoped
public class FromCamelHistoryFiller {

    private static final Logger log = Logger.getLogger(FromCamelHistoryFiller.class.getName());

    @Inject
    NotificationResources notificationResources;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    @Incoming("fromCamel")
    // Can be modified to use Multi<Message<String>> input also for more concurrency
    public Uni<Void> processAsync(Message<String> input) {
        return Uni.createFrom().item(() -> input.getPayload())
                .onItem().invoke(payload -> log.info(() -> "Processing return from camel: " + payload))
                .onItem().transform(this::decodeItem)
                .onItem()
                .transformToUni(payload -> {
                    return sessionFactory.withStatelessSession(statelessSession -> {
                        return notificationResources.updateHistoryItem(payload)
                                .onFailure().invoke(t -> log.info(() -> "|  Update Fail: " + t)
                                );
                    });
                })
                .onItemOrFailure()
                .transformToUni((unused, t) -> {
                    if (t != null) {
                        log.severe("|  Failure to update the history : " + t);
                    }
                    return Uni.createFrom().voidItem();
                });
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
