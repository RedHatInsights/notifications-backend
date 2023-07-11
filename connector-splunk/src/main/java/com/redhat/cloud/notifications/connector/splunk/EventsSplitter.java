package com.redhat.cloud.notifications.connector.splunk;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import javax.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;

/**
 * This processor splits one incoming payload with multiple events into several concatenated outgoing payloads
 * with a single event each. This allows us to send multiple events to Splunk HEC with a single HTTP request.
 */
@ApplicationScoped
public class EventsSplitter implements Processor {

    public static final String SOURCE_KEY = "source";
    public static final String SOURCE_VALUE = "eventing";
    public static final String SOURCE_TYPE_KEY = "sourcetype";
    public static final String SOURCE_TYPE_VALUE = "Insights event";
    public static final String EVENT_KEY = "event";

    @Override
    public void process(Exchange exchange) {

        Message in = exchange.getIn();
        JsonObject incomingBody = new JsonObject(in.getBody(String.class));

        // This method only alters the payload of the exchange if it contains the "events" key.
        if (incomingBody.containsKey("events")) {
            JsonArray events = incomingBody.getJsonArray("events");
            String outgoingBody;

            if (events.isEmpty()) {
                // If the events collection is empty, we're done.
                return;

            } else if (events.size() == 1) {
                // If there's only one event, the incoming payload simply needs to be wrapped into the data structure expected by Splunk HEC.
                outgoingBody = wrapPayload(incomingBody).encode();

            } else {
                // Otherwise, the incoming payload needs to be split.
                List<String> wrappedPayloads = new ArrayList<>();
                for (int i = 0; i < events.size(); i++) {
                    // Each event will use the incoming payload as a base for its data.
                    JsonObject singleEventPayload = incomingBody.copy();
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

            // The initial body is replaced with whatever this method produced.
            in.setBody(outgoingBody);
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
