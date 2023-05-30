package com.redhat.cloud.notifications.exports.transformers.event;

import com.redhat.cloud.notifications.exports.transformers.ResultsTransformer;
import com.redhat.cloud.notifications.models.Event;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.ZoneOffset;
import java.util.List;

public final class JSONEventTransformer implements ResultsTransformer<Event> {
    /**
     * Transforms the given list of events to JSON.
     * @param events the list of events to transform.
     * @return a {@link String} with the transformed contents.
     */
    @Override
    public String transform(final List<Event> events) {
        final JsonArray jsonEvents = new JsonArray();

        for (final Event event : events) {
            final JsonObject jsonEvent = new JsonObject();

            jsonEvent.put("uuid", event.getId());
            jsonEvent.put("bundle", event.getBundleDisplayName());
            jsonEvent.put("application", event.getApplicationDisplayName());
            jsonEvent.put("eventType", event.getEventTypeDisplayName());
            jsonEvent.put("created", event.getCreated().toInstant(ZoneOffset.UTC));

            jsonEvents.add(jsonEvent);
        }

        return jsonEvents.encode();
    }
}
