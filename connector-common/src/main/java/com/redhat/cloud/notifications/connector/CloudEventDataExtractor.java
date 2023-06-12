package com.redhat.cloud.notifications.connector;

import io.quarkus.arc.DefaultBean;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;

import javax.enterprise.context.ApplicationScoped;

/**
 * Extend this class in an {@link ApplicationScoped} bean from a connector Maven module to extract the
 * data from the {@code cloudEventData} parameter and store that data using the properties or headers
 * of the {@code exchange} parameter.
 */
@DefaultBean
@ApplicationScoped
public class CloudEventDataExtractor {

    public void extract(Exchange exchange, JsonObject cloudEventData) {
        /*
         * Usage example:
         * exchange.setProperty("foo", cloudEventData.getString("bar"));
         */
    }
}
