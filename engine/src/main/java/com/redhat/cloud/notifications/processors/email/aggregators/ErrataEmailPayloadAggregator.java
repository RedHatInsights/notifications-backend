package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;

public class ErrataEmailPayloadAggregator extends AbstractEmailPayloadAggregator {

    private static final String EVENT_TYPE = "event_type";

    private static final String ERRATA_KEY = "errata";
    private static final String EVENTS_KEY = "events";
    private static final String CONTEXT_KEY = "context";
    private static final String PAYLOAD_KEY = "payload";
    private static final String BASE_URL_KEY = "base_url";

    public static final String EVENT_TYPE_BUGFIX = "new-subscription-bugfix-errata";
    public static final String EVENT_TYPE_ENHANCEMENT = "new-subscription-enhancement-errata";
    public static final String EVENT_TYPE_SECURITY = "new-subscription-security-errata";

    private static final List<String> EVENT_TYPES = List.of(
        EVENT_TYPE_BUGFIX,
        EVENT_TYPE_ENHANCEMENT,
        EVENT_TYPE_SECURITY
    );

    public ErrataEmailPayloadAggregator() {
        JsonObject errata = new JsonObject();
        errata.put(EVENT_TYPE_BUGFIX, new JsonArray());
        errata.put(EVENT_TYPE_ENHANCEMENT, new JsonArray());
        errata.put(EVENT_TYPE_SECURITY, new JsonArray());
        context.put(ERRATA_KEY, errata);
    }

    @Override
    void processEmailAggregation(EmailAggregation notification) {
        JsonObject errata = context.getJsonObject(ERRATA_KEY);
        JsonObject notificationJson = notification.getPayload();
        JsonObject context = notificationJson.getJsonObject(CONTEXT_KEY);
        String eventType = notificationJson.getString(EVENT_TYPE);

        if (!EVENT_TYPES.contains(eventType)) {
            return;
        }

        if (!errata.containsKey(BASE_URL_KEY) && context.containsKey(BASE_URL_KEY)) {
            errata.put(BASE_URL_KEY, context.getString(BASE_URL_KEY));
        }

        notificationJson.getJsonArray(EVENTS_KEY).stream().forEach(eventObject -> {
            JsonObject event = (JsonObject) eventObject;
            JsonObject payload = event.getJsonObject(PAYLOAD_KEY);

            errata.getJsonArray(eventType).add(payload);
        });
    }
}
