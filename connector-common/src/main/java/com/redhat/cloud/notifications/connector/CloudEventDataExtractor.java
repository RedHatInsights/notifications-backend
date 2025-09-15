package com.redhat.cloud.notifications.connector;

import io.quarkus.arc.DefaultBean;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Extend this class in an {@link ApplicationScoped} bean from a connector Maven module to extract the
 * data from the {@code cloudEventData} parameter and store that data in the {@code context} parameter.
 * This is the new version that replaces the Camel-based CloudEventDataExtractor.
 */
@DefaultBean
@ApplicationScoped
public class CloudEventDataExtractor {

    public void extract(ExceptionProcessor.ProcessingContext context, JsonObject cloudEventData) throws Exception {
        /*
         * Usage example in connector-specific implementations:
         * context.setTargetUrl(cloudEventData.getString("target_url"));
         * context.setAdditionalProperty("foo", cloudEventData.getString("bar"));
         *
         * The default implementation does nothing, allowing connectors to override
         * this method to extract their specific data requirements.
         */
    }
}
