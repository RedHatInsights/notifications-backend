package com.redhat.cloud.notifications.connector.google.chat;

import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URLDecoder;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.LoggingLevel.INFO;

@ApplicationScoped
public class GoogleChatRouteBuilder extends EngineToConnectorRouteBuilder {

    @Inject
    HttpConnectorConfig connectorConfig;

    @Override
    public void configureRoutes() {
        from(seda(ENGINE_TO_CONNECTOR))
                .routeId(connectorConfig.getConnectorName())
                .setHeader(HTTP_METHOD, constant("POST"))
                .setHeader(CONTENT_TYPE, constant("application/json"))
                /*
                 * Webhook urls provided by Google have already encoded parameters, by default, Camel will also encode endpoint urls.
                 * Parameters such as token values, won't be usable if they are encoded twice.
                 * To avoid double encoding, Camel provides the `RAW()` instruction. It can be applied to parameters values but not on full url.
                 * That involve to split Urls parameters, surround each value by `RAW()` instruction, then concat all those to rebuild endpoint url.
                 * To avoid all those steps, we decode the full url, then Camel will encode it to send the expected format to Google servers.
                 */
                .toD(URLDecoder.decode("${exchangeProperty." + TARGET_URL + "}", UTF_8), connectorConfig.getEndpointCacheMaxSize())
                .log(INFO, getClass().getName(), "Sent Google Chat notification [orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "}]")
                .to(direct(SUCCESS));
    }
}
