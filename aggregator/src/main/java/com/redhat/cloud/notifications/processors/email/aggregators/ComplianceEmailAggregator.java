package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.List;

public class ComplianceEmailAggregator extends AbstractEmailPayloadAggregator {

    private static final String EVENT_TYPE = "event_type";
    private static final String REPORT_UPLOAD_FAILED = "report-upload-failed";
    private static final String SYSTEM_NON_COMPLIANT = "compliance-below-threshold";

    private static final List<String> EVENT_TYPES = Arrays.asList(REPORT_UPLOAD_FAILED, SYSTEM_NON_COMPLIANT);

    private static final String COMPLIANCE_KEY = "compliance";
    private static final String EVENTS_KEY = "events";
    private static final String PAYLOAD_KEY = "payload";

    public ComplianceEmailAggregator() {
        JsonObject compliance = new JsonObject();

        compliance.put(REPORT_UPLOAD_FAILED, new JsonArray());
        compliance.put(SYSTEM_NON_COMPLIANT, new JsonArray());

        context.put(COMPLIANCE_KEY, compliance);
    }

    @Override
    void processEmailAggregation(EmailAggregation notification) {
        JsonObject notificationJson = notification.getPayload();
        String eventType = notificationJson.getString(EVENT_TYPE);

        JsonObject compliance = this.context.getJsonObject(COMPLIANCE_KEY);

        // Ignore events that are not declared among the supported event types
        if (!EVENT_TYPES.contains(eventType)) {
            return;
        }

        JsonArray events = notificationJson.getJsonArray(EVENTS_KEY);
        // A malformed event with no events array would NPE below; skip it instead of crashing the
        // whole fold (same guard as ApplicationServicesEmailPayloadAggregator).
        if (events == null) {
            Log.warnf("Skipping Compliance aggregation event with no events array, orgId=%s", getOrgId());
            return;
        }

        events.stream().forEach(eventObject -> {
            JsonObject event = (JsonObject) eventObject;
            JsonObject payload = event.getJsonObject(PAYLOAD_KEY);
            // A malformed event with no payload would NPE in tagMetadata() below; skip it instead of
            // crashing the whole fold (same guard as ApplicationServicesEmailPayloadAggregator).
            if (payload == null) {
                Log.warnf("Skipping Compliance aggregation event with no payload, orgId=%s", getOrgId());
                return;
            }
            tagMetadata(payload, notification);

            JsonArray collection = compliance.getJsonArray(eventType);
            collection.add(payload);
        });
    }
}
