package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class PatchEmailPayloadAggregator extends AbstractEmailPayloadAggregator {

    // Notification common
    private static final String EVENT_TYPE_KEY = "event_type";
    private static final String EVENTS_KEY = "events";
    private static final String PAYLOAD_KEY = "payload";

    // Patch event payload
    private static final String ADVISORY_NAME = "advisory_name";
    private static final String ADVISORY_TYPE = "advisory_type";
    private static final String SYNOPSIS = "synopsis";

    // Patch aggregator
    private static final String PATCH_KEY = "patch";

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

    // Maximum number of advisories to keep per type to prevent memory issues
    public static final int MAXIMUM_ADVISORIES_PER_TYPE = 500;

    private final JsonArray securityAdvisories = new JsonArray();
    private final JsonArray bugfixAdvisories = new JsonArray();
    private final JsonArray enhancementAdvisories = new JsonArray();
    private final JsonArray otherAdvisories = new JsonArray();

    public PatchEmailPayloadAggregator() {
    }

    @Override
    public boolean isEmpty() {
        return totalAdvisories.get() == 0;
    }

    @Override
    void processEmailAggregation(EmailAggregation notification) {
        JsonObject notificationPayload = notification.getPayload();
        String eventType = notificationPayload.getString(EVENT_TYPE_KEY);

        if (!EVENT_TYPES.contains(eventType)) {
            return;
        }

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
            String synopsis = payload.getString(SYNOPSIS);
            JsonObject advisory = new JsonObject().put("name", advisoryName).put("synopsis", synopsis);

            // Group unspecified advisories under other type
            if (advisoryType.equals(UNSPECIFIED_TYPE)) {
                advisoryType = OTHER_TYPE;
            }

            switch (advisoryType) {
                case SECURITY_TYPE:
                    if (securityAdvisories.size() < MAXIMUM_ADVISORIES_PER_TYPE) {
                        securityAdvisories.add(advisory);
                    }
                    break;
                case BUGFIX_TYPE:
                    if (bugfixAdvisories.size() < MAXIMUM_ADVISORIES_PER_TYPE) {
                        bugfixAdvisories.add(advisory);
                    }
                    break;
                case ENHANCEMENT_TYPE:
                    if (enhancementAdvisories.size() < MAXIMUM_ADVISORIES_PER_TYPE) {
                        enhancementAdvisories.add(advisory);
                    }
                    break;
                case OTHER_TYPE:
                    if (otherAdvisories.size() < MAXIMUM_ADVISORIES_PER_TYPE) {
                        otherAdvisories.add(advisory);
                    }
                    break;
                default:
                    Log.debugf("Unknown advisory type: %s", advisoryType);
                    break;
            }

            totalAdvisories.incrementAndGet();
        });
    }

    @Override
    public Map<String, Object> getContext() {
        // Populate the context with the final aggregated data
        JsonObject patch = new JsonObject();
        patch.put(SECURITY_TYPE, securityAdvisories);
        patch.put(BUGFIX_TYPE, bugfixAdvisories);
        patch.put(ENHANCEMENT_TYPE, enhancementAdvisories);
        patch.put(OTHER_TYPE, otherAdvisories);

        context.put(PATCH_KEY, patch);
        context.put(TOTAL_ADVISORIES, totalAdvisories);

        return super.getContext();
    }
}
