package com.redhat.cloud.notifications.connector.splunk;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

/**
 * This processor splits one incoming payload with multiple events into several concatenated outgoing payloads
 * with a single event each. This allows us to send multiple events to Splunk HEC with a single HTTP request.
 * This is the new version that replaces the Camel-based EventsSplitter.
 */
@ApplicationScoped
public class EventsSplitter {

    public static final String SOURCE_KEY = "source";
    public static final String SOURCE_VALUE = "eventing";
    public static final String SOURCE_TYPE_KEY = "sourcetype";
    public static final String SOURCE_TYPE_VALUE = "Insights event";
    public static final String EVENT_KEY = "event";

    public String splitEvents(JsonObject cloudEvent) {
        // Extract the data section from the CloudEvent
        JsonObject data = cloudEvent.getJsonObject("data");
        if (data == null) {
            // If no data section, return empty payload
            return "";
        }

        // This method only alters the payload if it contains the "events" key.
        if (data.containsKey("events")) {
            JsonArray events = data.getJsonArray("events");
            String outgoingBody;

            if (events.isEmpty()) {
                // If the events collection is empty, we're done.
                return "";

            } else if (events.size() == 1) {
                // If there's only one event, the incoming payload simply needs to be wrapped into the data structure expected by Splunk HEC.
                outgoingBody = wrapPayload(data).encode();

            } else {
                // Otherwise, the incoming payload needs to be split.
                List<String> wrappedPayloads = new ArrayList<>();
                for (int i = 0; i < events.size(); i++) {
                    // Each event will use the incoming payload as a base for its data.
                    JsonObject singleEventPayload = data.copy();
                    // The initial events list from that payload is replaced with a single event.
                    singleEventPayload.put("events", new JsonArray(List.of(events.getJsonObject(i))));
                    // The new payload is wrapped into the data structure expected by Splunk HEC.
                    JsonObject wrappedPayload = wrapPayload(singleEventPayload);
                    // All wrapped payloads are transformed into a String and collected into a list which is eventually concatenated.
                    wrappedPayloads.add(wrappedPayload.encode());
                }
                // It's time to concatenate everything into a single outgoing body.
                outgoingBody = String.join("", wrappedPayloads);
            }

            return outgoingBody;
        } else {
            // If no events key, wrap the entire data payload
            return wrapPayload(data).encode();
        }
    }

    /**
     * Wraps a payload into the data structure expected by Splunk HEC.
     */
    private static JsonObject wrapPayload(JsonObject payload) {
        JsonObject result = new JsonObject();
        result.put(SOURCE_KEY, SOURCE_VALUE);
        result.put(SOURCE_TYPE_KEY, SOURCE_TYPE_VALUE);
        result.put(EVENT_KEY, payload);
        return result;
    }
}
