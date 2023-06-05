package com.redhat.cloud.notifications.processors.camel.google.chat;

import com.redhat.cloud.notifications.processors.camel.CamelRouteBuilder;
import com.redhat.cloud.notifications.processors.camel.IncomingCloudEventFilter;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.net.URLDecoder;

import static com.redhat.cloud.notifications.events.EndpointProcessor.GOOGLE_CHAT_ENDPOINT_SUBTYPE;
import static com.redhat.cloud.notifications.models.HttpType.POST;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.WEBHOOK_URL;
import static com.redhat.cloud.notifications.processors.camel.ReturnRouteBuilder.RETURN_ROUTE_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.LoggingLevel.INFO;

@ApplicationScoped
public class GoogleChatRouteBuilder extends CamelRouteBuilder {

    public static final String GOOGLE_CHAT_ROUTE = "google-chat";

    private static final String KAFKA_GROUP_ID = "notifications-connector-google-chat";

    @Inject
    GoogleChatNotificationProcessor googleChatNotificationProcessor;

    @Override
    public void configure() {

        configureCommonExceptionHandler();

        from(kafka(toCamelTopic).groupId(KAFKA_GROUP_ID))
                .routeId(GOOGLE_CHAT_ROUTE)
                .filter(new IncomingCloudEventFilter(GOOGLE_CHAT_ENDPOINT_SUBTYPE))
                .log(INFO, "Received ${body}")
                .process(googleChatNotificationProcessor)
                .removeHeaders(CAMEL_HTTP_HEADERS_PATTERN)
                .setHeader(HTTP_METHOD, constant(POST))
                .setHeader(CONTENT_TYPE, constant(APPLICATION_JSON))
                /*
                 * Webhook urls provided by Google have already encoded parameters, by default, Camel will also encode endpoint urls.
                 * Parameters such as token values, won't be usable if they are encoded twice.
                 * To avoid double encoding, Camel provides the `RAW()` instruction. It can be applied to parameters values but not on full url.
                 * That involve to split Urls parameters, surround each value by `RAW()` instruction, then concat all those to rebuild endpoint url.
                 * To avoid all those steps, we decode the full url, then Camel will encode it to send the expected format to Google servers.
                 */
                .toD(URLDecoder.decode("${exchangeProperty." + WEBHOOK_URL + "}", UTF_8), maxEndpointCacheSize)
                .setProperty(SUCCESSFUL, constant(true))
                .setProperty(OUTCOME, simple("Event ${exchangeProperty." + ID + "} sent successfully"))
                .to(direct(RETURN_ROUTE_NAME));
    }
}
