package com.redhat.cloud.notifications.exports.transformers.event;

import com.redhat.cloud.notifications.exports.transformers.ResultsTransformer;
import com.redhat.cloud.notifications.models.Event;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.ZoneOffset;
import java.util.List;

public final class JSONEventTransformer implements ResultsTransformer<Event> {

    private final JsonArray jsonEvents = new JsonArray();

    /**
     * Appends the given page of events as JSON objects.
     * @param events the page of events to append.
     */
    @Override
    public void addRecords(final List<Event> events) {
        for (final Event event : events) {
            final JsonObject jsonEvent = new JsonObject();

            jsonEvent.put("uuid", event.getId());
            jsonEvent.put("bundle", event.getBundleDisplayName());
            jsonEvent.put("application", event.getApplicationDisplayName());
            jsonEvent.put("eventType", event.getEventTypeDisplayName());
            jsonEvent.put("created", event.getCreated().toInstant(ZoneOffset.UTC));

            this.jsonEvents.add(jsonEvent);
        }
    }

    /**
     * Encodes the accumulated JSON array.
     * @return a {@link String} with the transformed contents.
     */
    @Override
    public String finish() {
        return this.jsonEvents.encode();
    }
}
