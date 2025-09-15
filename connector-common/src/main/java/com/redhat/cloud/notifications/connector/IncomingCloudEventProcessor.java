package com.redhat.cloud.notifications.connector;

import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Processes incoming CloudEvents and extracts relevant data.
 * This is the new version that replaces the Camel-based IncomingCloudEventProcessor.
 */
@ApplicationScoped
public class IncomingCloudEventProcessor {

    public static final String CLOUD_EVENT_ID = "id";
    public static final String CLOUD_EVENT_TYPE = "type";
    public static final String CLOUD_EVENT_DATA = "data";

    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    CloudEventDataExtractor cloudEventDataExtractor;

    public ExceptionProcessor.ProcessingContext process(JsonObject cloudEvent) throws Exception {
        ExceptionProcessor.ProcessingContext context = new ExceptionProcessor.ProcessingContext();

        // Store the original cloud event
        context.setOriginalCloudEvent(cloudEvent);

        // Extract basic properties
        context.setId(cloudEvent.getString(CLOUD_EVENT_ID));
        context.setRouteId(connectorConfig.getConnectorName());

        // Extract data section
        JsonObject data = cloudEvent.getJsonObject(CLOUD_EVENT_DATA);
        if (data != null) {
            context.setOrgId(data.getString("org_id"));

            // Use the cloud event data extractor to populate additional context
            cloudEventDataExtractor.extract(context, data);
        }

        return context;
    }
}
