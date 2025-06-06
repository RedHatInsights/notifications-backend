package com.redhat.cloud.notifications.connector.servicenow;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationDataExtractor;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.hc.core5.http.URIScheme;
import org.apache.http.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.servicenow.ExchangeProperty.ACCOUNT_ID;
import static com.redhat.cloud.notifications.connector.servicenow.ExchangeProperty.TARGET_URL_NO_SCHEME;
import static com.redhat.cloud.notifications.connector.servicenow.ExchangeProperty.TRUST_ALL;

@ApplicationScoped
public class ServiceNowCloudEventDataExtractor extends CloudEventDataExtractor {

    public static final String NOTIF_METADATA = "notif-metadata";

    @Inject
    AuthenticationDataExtractor authenticationDataExtractor;

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) throws Exception {

        exchange.setProperty(ACCOUNT_ID, cloudEventData.getString("account_id"));

        JsonObject metadata = cloudEventData.getJsonObject(NOTIF_METADATA);
        exchange.setProperty(TARGET_URL, metadata.getString("url"));
        exchange.setProperty(TRUST_ALL, Boolean.valueOf(metadata.getString("trustAll")));

        JsonObject authentication = metadata.getJsonObject("authentication");
        authenticationDataExtractor.extract(exchange, authentication);

        cloudEventData.remove(NOTIF_METADATA);

        validateTargetUrl(exchange);
        exchange.setProperty(TARGET_URL_NO_SCHEME, exchange.getProperty(TARGET_URL, String.class).replace("https://", ""));

        exchange.getIn().setBody(cloudEventData.encode());
    }

    private void validateTargetUrl(Exchange exchange) throws Exception {
        String targetUrl = exchange.getProperty(TARGET_URL, String.class);
        try {
            String scheme = (new URI(targetUrl)).getScheme();

            if (URIScheme.HTTP.same(scheme)) {
                throw new ProtocolException("HTTP protocol is not supported");
            } else if (!URIScheme.HTTPS.same(scheme)) {
                throw new IllegalArgumentException("URL validation failed");
            }
            // handle case where url is null (should never happen)
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid target URL: " + targetUrl);
        }
    }
}
