package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;

@ApplicationScoped
public class DrawerCloudEventDataExtractor extends CloudEventDataExtractor {

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) {

    }
}
