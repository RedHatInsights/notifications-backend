package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Unlike the legacy per-recipient engine aggregator this was ported from, this class does not track a
 * {@code global_releases_number} running total -- this instance folds every recipient's events into one
 * superset, so any such count would already be wrong for every recipient whose trimmed view differs from
 * the full digest. Engine recomputes it per recipient after trimming instead (see
 * {@code EmailAggregationProcessor.recomputeApplicationServicesGlobalReleasesNumber()}).
 */
public class ApplicationServicesEmailPayloadAggregator extends AbstractEmailPayloadAggregator {

    private static final String EVENT_TYPE = "event_type";

    private static final String APPLICATION_SERVICES_KEY = "application-services";
    private static final String PRODUCTS_KEY = "products";
    private static final String EVENTS_KEY = "events";
    private static final String CONTEXT_KEY = "context";
    private static final String PAYLOAD_KEY = "payload";
    private static final String SOURCE_KEY = "source";
    private static final String DISPLAY_NAME_KEY = "display_name";
    private static final String DESCRIPTION_KEY = "description";
    private static final String PAYLOADS_KEY = "payloads";

    public ApplicationServicesEmailPayloadAggregator() {
        JsonObject applicationServices = new JsonObject();
        applicationServices.put(PRODUCTS_KEY, new JsonObject());
        context.put(APPLICATION_SERVICES_KEY, applicationServices);
    }

    @Override
    void processEmailAggregation(EmailAggregation notification) {
        try {
            JsonObject applicationServices = context.getJsonObject(APPLICATION_SERVICES_KEY);
            JsonObject products = applicationServices.getJsonObject(PRODUCTS_KEY);
            JsonObject notificationJson = notification.getPayload();
            String eventType = notificationJson.getString(EVENT_TYPE);
            JsonObject payloadContext = notificationJson.getJsonObject(CONTEXT_KEY);

            if (eventType == null || payloadContext == null) {
                Log.debugf("Skipping Application Services aggregation: eventType=%s, hasContext=%b, orgId=%s",
                    eventType, payloadContext != null, getOrgId());
                return;
            }

            if (!products.containsKey(eventType)) {
                JsonObject source = notificationJson.getJsonObject(SOURCE_KEY);
                String eventTypeDisplayName = source != null && source.getJsonObject(EVENT_TYPE) != null
                    ? source.getJsonObject(EVENT_TYPE).getString(DISPLAY_NAME_KEY)
                    : null;
                if (eventTypeDisplayName == null) {
                    Log.debugf("Skipping Application Services product initialization: missing event type display name for eventType=%s, orgId=%s",
                        eventType, getOrgId());
                    return;
                }
                JsonObject eventTypeObject = new JsonObject();
                eventTypeObject.put(DESCRIPTION_KEY, eventTypeDisplayName);
                eventTypeObject.put(PAYLOADS_KEY, new JsonArray());
                products.put(eventType, eventTypeObject);
            }

            JsonArray events = notificationJson.getJsonArray(EVENTS_KEY);
            if (events == null) {
                Log.debugf("Skipping Application Services aggregation: no events array for eventType=%s, orgId=%s",
                    eventType, getOrgId());
                return;
            }

            JsonArray targetPayloads = products.getJsonObject(eventType).getJsonArray(PAYLOADS_KEY);
            for (int i = 0; i < events.size(); i++) {
                JsonObject event = events.getJsonObject(i);
                if (event == null) {
                    continue;
                }
                JsonObject payload = event.getJsonObject(PAYLOAD_KEY);
                if (payload != null) {
                    tagMetadata(payload, notification);
                    targetPayloads.add(payload);
                }
            }
        } catch (Exception e) {
            Log.warnf(e, "Failed to process Application Services aggregation for orgId=%s", getOrgId());
        }
    }
}
