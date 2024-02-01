package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.component.http.HttpMethods;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.MissingResourceException;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.secrets.SecretsExchangeProperty.SECRET_ID;
import static com.redhat.cloud.notifications.connector.secrets.SecretsExchangeProperty.SECRET_PASSWORD;
import static com.redhat.cloud.notifications.connector.secrets.SecretsExchangeProperty.SECRET_USERNAME;
import static com.redhat.cloud.notifications.connector.webhook.AuthenticationType.BASIC;
import static com.redhat.cloud.notifications.connector.webhook.AuthenticationType.BEARER;
import static com.redhat.cloud.notifications.connector.webhook.AuthenticationType.SECRET_TOKEN;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.AUTHENTICATION_TYPE;
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
        extractLegacyAuthData(exchange, endpointProperties);

        JsonObject authentication = cloudEventData.getJsonObject("authentication");
        if (authentication != null) {
            String type = authentication.getString("type");
            if (type != null) {
                try {
                    AuthenticationType authType = AuthenticationType.valueOf(type);
                    exchange.setProperty(AUTHENTICATION_TYPE, authType);
                    Long secretId = authentication.getLong("secretId");
                    if (secretId != null) {
                        exchange.setProperty(SECRET_ID, secretId);
                    }
                } catch (IllegalArgumentException e) {
                    Log.errorf("Unknown authentication type: %s", type);
                }
            }
        }
        cloudEventData.remove("authentication");

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

    // TODO RHCLOUD-24930 Remove this method after the migration is done.
    @Deprecated(forRemoval = true)
    private void extractLegacyAuthData(Exchange exchange, JsonObject endpointProperties) {
        if (isNotNullOrBlank(endpointProperties.getString("secret_token"))) {
            exchange.setProperty(AUTHENTICATION_TYPE, SECRET_TOKEN);
            exchange.setProperty(SECRET_PASSWORD, endpointProperties.getString("secret_token"));
        } else if (isNotNullOrBlank(endpointProperties.getString("bearer_token"))) {
            exchange.setProperty(AUTHENTICATION_TYPE, BEARER);
            exchange.setProperty(SECRET_PASSWORD, endpointProperties.getString("bearer_token"));
        } else {
            JsonObject basicAuth = endpointProperties.getJsonObject(BASIC_AUTHENTICATION);
            if (basicAuth != null && isNotNullOrBlank(basicAuth.getString("username")) && isNotNullOrBlank(basicAuth.getString("password"))) {
                exchange.setProperty(AUTHENTICATION_TYPE, BASIC);
                exchange.setProperty(SECRET_USERNAME, basicAuth.getString("username"));
                exchange.setProperty(SECRET_PASSWORD, basicAuth.getString("password"));
            }
        }
    }

    private static boolean isNotNullOrBlank(String value) {
        if (value == null) {
            return false;
        }
        return !value.isBlank();
    }
}
