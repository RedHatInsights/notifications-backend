package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // Patch aggregator
    private static final String PATCH_KEY = "patch";
    private static final String ADVISORIES_KEY = "advisories";

    private static final String NEW_ADVISORIES_EVENT = "new-advisories";
    private static final List<String> EVENT_TYPES = Arrays.asList(NEW_ADVISORIES_EVENT);

    private static final String ENHANCEMENT_TYPE = "enhancement";
    private static final String BUGFIX_TYPE = "bugfix";
    private static final String SECURITY_TYPE = "security";
    private static final String UNSPECIFIED_TYPE = "unspecified";
    private static final List<String> ADVISORY_TYPES = Arrays.asList(ENHANCEMENT_TYPE, BUGFIX_TYPE, SECURITY_TYPE, UNSPECIFIED_TYPE);

    private final Set<String> uniqueHosts = new HashSet<>();
    private final Map<String, HashSet<String>> uniqueHostsPerAdvisoryType = new HashMap<>() {
        {
            put(ENHANCEMENT_TYPE, new HashSet<>());
            put(BUGFIX_TYPE, new HashSet<>());
            put(SECURITY_TYPE, new HashSet<>());
            put(UNSPECIFIED_TYPE, new HashSet<>());
        }
    };
    private static final String UNIQUE_HOSTS_CNT = "unique_system_count";

    public PatchEmailPayloadAggregator() {
        JsonObject patch = new JsonObject();

        patch.put(ENHANCEMENT_TYPE, new JsonObject());
        patch.put(BUGFIX_TYPE, new JsonObject());
        patch.put(SECURITY_TYPE, new JsonObject());
        patch.put(UNSPECIFIED_TYPE, new JsonObject());

        patch.forEach(advisoryType -> {
            patch.getJsonObject(advisoryType.getKey()).put(ADVISORIES_KEY, new JsonObject());
        });

        context.put(PATCH_KEY, patch);
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
        String inventoryID = payloadContext.getString(INVENTORY_ID);

        // Put and group advisories
        notificationPayload.getJsonArray(EVENTS_KEY).stream().forEach(eventObject -> {
            JsonObject event = (JsonObject) eventObject;
            JsonObject payload = event.getJsonObject(PAYLOAD_KEY);
            String advisoryType = payload.getString(ADVISORY_TYPE).toLowerCase();

            // Drop on unknown type
            if (!ADVISORY_TYPES.contains(advisoryType)) {
                return;
            }

            JsonObject newAdvisory = new JsonObject();
            String advisoryName = payload.getString(ADVISORY_NAME);
            newAdvisory.put(ADVISORY_NAME, advisoryName);
            patch.getJsonObject(advisoryType).getJsonObject(ADVISORIES_KEY).put(advisoryName, newAdvisory);

            uniqueHostsPerAdvisoryType.get(advisoryType).add(inventoryID);
        });

        // Put unique systems count per each advisory type
        patch.forEach(advisoryType -> {
            String key = advisoryType.getKey();
            if (ADVISORY_TYPES.contains(key)) {
                patch.getJsonObject(key).put(UNIQUE_HOSTS_CNT, uniqueHostsPerAdvisoryType.get(key).size());
            }
        });

        // Put total unique systems count
        uniqueHosts.add(inventoryID);
        context.put(UNIQUE_HOSTS_CNT, uniqueHosts.size());
    }
}
