package com.redhat.cloud.notifications.processors.camel.google.chat;

import com.redhat.cloud.notifications.processors.camel.CamelCommonExceptionHandler;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.net.URLDecoder;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.models.HttpType.POST;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.processors.camel.ReturnRouteBuilder.RETURN_ROUTE_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.direct;

@ApplicationScoped
public class GoogleChatRouteBuilder extends CamelCommonExceptionHandler {

    public static final String REST_PATH = API_INTERNAL + "/google-chat";
    public static final String GOOGLE_CHAT_OUTGOING_ROUTE = "google-chat-outgoing";
    public static final String GOOGLE_CHAT_INCOMING_ROUTE = "google-chat-incoming";
    private static final String DIRECT_ENDPOINT = "direct:google-chat";

    @Inject
    GoogleChatNotificationProcessor googleChatNotificationProcessor;

    @Override
    public void configure() {

        configureCommonExceptionHandler();

        /*
         * This route exposes a REST endpoint that is used from GoogleChatProcessor to send a Google Chat notification.
         */
        rest(REST_PATH)
                .post()
                .consumes(APPLICATION_JSON)
                .routeId(GOOGLE_CHAT_INCOMING_ROUTE)
                .to(DIRECT_ENDPOINT);

        /*
         * This route transforms an incoming REST payload into a message that is eventually sent to Google Chat.
         */
        from(DIRECT_ENDPOINT)
                .routeId(GOOGLE_CHAT_OUTGOING_ROUTE)
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
                .toD(URLDecoder.decode("${exchangeProperty.webhookUrl}", UTF_8), maxEndpointCacheSize)
                .setProperty(SUCCESSFUL, constant(true))
                .setProperty(OUTCOME, simple("Event ${exchangeProperty.historyId} sent successfully"))
                .to(direct(RETURN_ROUTE_NAME));
    }
}
