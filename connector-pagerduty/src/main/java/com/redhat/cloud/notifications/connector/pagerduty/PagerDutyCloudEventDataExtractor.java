package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationDataExtractor;
import com.redhat.cloud.notifications.connector.pagerduty.config.PagerDutyConnectorConfig;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.PAYLOAD;

@ApplicationScoped
public class PagerDutyCloudEventDataExtractor extends CloudEventDataExtractor {

    public static final String AUTHENTICATION = "authentication";

    @Inject
    PagerDutyConnectorConfig config;

    @Inject
    AuthenticationDataExtractor authenticationDataExtractor;

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) {

        exchange.setProperty(TARGET_URL, config.getPagerDutyUrl());

        JsonObject authentication = cloudEventData.getJsonObject(AUTHENTICATION);
        authenticationDataExtractor.extract(exchange, authentication);

        exchange.getIn().setBody(cloudEventData.getJsonObject(PAYLOAD));
    }
}
