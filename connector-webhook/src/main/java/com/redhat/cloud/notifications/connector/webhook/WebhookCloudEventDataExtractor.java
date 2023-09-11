package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.component.http.HttpMethods;
import javax.enterprise.context.ApplicationScoped;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.MissingResourceException;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.BASIC_AUTH_PASSWORD;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.BASIC_AUTH_USERNAME;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.BEARER_TOKEN;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.INSIGHT_TOKEN_HEADER;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.TARGET_URL_NO_SCHEME;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.TRUST_ALL;

@ApplicationScoped
public class WebhookCloudEventDataExtractor extends CloudEventDataExtractor {

    public static final String ENDPOINT_PROPERTIES = "endpoint_properties";
    public static final String PAYLOAD = "payload";

    public static final String BASIC_AUTHENTICATION = "basic_authentication";

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) throws MalformedURLException {
        checkPayload(cloudEventData);
        JsonObject endpointProperties = cloudEventData.getJsonObject(ENDPOINT_PROPERTIES);
        exchange.setProperty(TARGET_URL, endpointProperties.getString("url"));
        if (Boolean.TRUE == endpointProperties.getBoolean("disable_ssl_verification") &&
            exchange.getProperty(TARGET_URL, String.class).startsWith("https://")) {
            exchange.setProperty(TRUST_ALL, Boolean.TRUE);
        }
        exchange.setProperty(TARGET_URL_NO_SCHEME, exchange.getProperty(TARGET_URL, String.class).replace("https://", ""));
        exchange.setProperty(INSIGHT_TOKEN_HEADER, endpointProperties.getString("secret_token"));
        exchange.setProperty(BEARER_TOKEN, endpointProperties.getString("bearer_token"));
        JsonObject basicAuth = endpointProperties.getJsonObject(BASIC_AUTHENTICATION);
        if (basicAuth != null) {
            exchange.setProperty(BASIC_AUTH_USERNAME, basicAuth.getString("username"));
            exchange.setProperty(BASIC_AUTH_PASSWORD, basicAuth.getString("password"));
        }

        if (cloudEventData.getJsonObject(PAYLOAD).containsKey("org_id")) {
            exchange.setProperty(ORG_ID, cloudEventData.getJsonObject(PAYLOAD).getString("org_id"));
        } else if (cloudEventData.getJsonObject(PAYLOAD).containsKey("redhatorgid")) {
            exchange.setProperty(ORG_ID, cloudEventData.getJsonObject(PAYLOAD).getString("redhatorgid"));
        }

        exchange.getIn().setBody(cloudEventData.getJsonObject(PAYLOAD).encode());
        exchange.getIn().setHeader(Exchange.HTTP_METHOD, HttpMethods.valueOf(endpointProperties.getString("method")));

    }

    private void checkPayload(JsonObject cloudEventData) throws MalformedURLException {
        if (null == cloudEventData.getJsonObject(ENDPOINT_PROPERTIES)) {
            throw new MissingResourceException("The '" + ENDPOINT_PROPERTIES + "' field is required", WebhookCloudEventDataExtractor.class.getName(), ENDPOINT_PROPERTIES);
        }

        if (null == cloudEventData.getJsonObject(ENDPOINT_PROPERTIES).getString("url")) {
            throw new MissingResourceException("The endpoint url is required", WebhookCloudEventDataExtractor.class.getName(), ENDPOINT_PROPERTIES + ".url");
        } else {
            new URL(cloudEventData.getJsonObject(ENDPOINT_PROPERTIES).getString("url"));
        }

        if (null == cloudEventData.getJsonObject(PAYLOAD)) {
            throw new MissingResourceException("The '" + PAYLOAD + "' field is required",  WebhookCloudEventDataExtractor.class.getName(), PAYLOAD);
        }
    }
}
