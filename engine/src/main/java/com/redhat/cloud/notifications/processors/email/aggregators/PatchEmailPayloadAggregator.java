package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class PatchEmailPayloadAggregator extends AbstractEmailPayloadAggregator {

    // Notification common
    private static final String CONTEXT_KEY = "context";
    private static final String EVENT_TYPE_KEY = "event_type";
    private static final String EVENTS_KEY = "events";
    private static final String PAYLOAD_KEY = "payload";

    // Patch context
    private static final String INVENTORY_ID = "inventory_id";

    // Patch event payload
    private static final String ADVISORY_NAME = "advisory_name";
    private static final String ADVISORY_TYPE = "advisory_type";
    private static final String SYNOPSIS = "synopsis";

    // Patch aggregator
    private static final String PATCH_KEY = "patch";
    private static final String ADVISORIES_KEY = "advisories";

    private static final String NEW_ADVISORIES_EVENT = "new-advisory";
    private static final List<String> EVENT_TYPES = Arrays.asList(NEW_ADVISORIES_EVENT);

    private static final String ENHANCEMENT_TYPE = "enhancement";
    private static final String BUGFIX_TYPE = "bugfix";
    private static final String SECURITY_TYPE = "security";
    private static final String UNSPECIFIED_TYPE = "unspecified";
    private static final String OTHER_TYPE = "other";
    private static final List<String> ADVISORY_TYPES = Arrays.asList(ENHANCEMENT_TYPE, BUGFIX_TYPE, SECURITY_TYPE, UNSPECIFIED_TYPE);

    private static final String TOTAL_ADVISORIES = "total_advisories";
    private final AtomicInteger totalAdvisories = new AtomicInteger(0);

    public PatchEmailPayloadAggregator() {
        JsonObject patch = new JsonObject();

        patch.put(SECURITY_TYPE, new JsonArray());
        patch.put(BUGFIX_TYPE, new JsonArray());
        patch.put(ENHANCEMENT_TYPE, new JsonArray());
        patch.put(OTHER_TYPE, new JsonArray());

        context.put(PATCH_KEY, patch);
        context.put(TOTAL_ADVISORIES, totalAdvisories);
    }

    @Override
    void processEmailAggregation(EmailAggregation notification) {
        JsonObject patch = context.getJsonObject(PATCH_KEY);
        JsonObject notificationPayload = notification.getPayload();
        String eventType = notificationPayload.getString(EVENT_TYPE_KEY);

        if (!EVENT_TYPES.contains(eventType)) {
            return;
        }

        JsonObject payloadContext = notificationPayload.getJsonObject(CONTEXT_KEY);

        // Put and group advisories
        notificationPayload.getJsonArray(EVENTS_KEY).stream().forEach(eventObject -> {
            JsonObject event = (JsonObject) eventObject;
            JsonObject payload = event.getJsonObject(PAYLOAD_KEY);
            String advisoryType = payload.getString(ADVISORY_TYPE).toLowerCase();

            // Drop on unknown type
            if (!ADVISORY_TYPES.contains(advisoryType)) {
                return;
            }

            String advisoryName = payload.getString(ADVISORY_NAME);

            // Group unspecified advisories under other type
            if (advisoryType.equals(UNSPECIFIED_TYPE)) {
                advisoryType = OTHER_TYPE.toString();
            }

            String synopsis = payload.getString(SYNOPSIS);
            patch.getJsonArray(advisoryType).add(new JsonObject().put("name", advisoryName).put("synopsis", synopsis));
            totalAdvisories.incrementAndGet();
        });
    }
}
