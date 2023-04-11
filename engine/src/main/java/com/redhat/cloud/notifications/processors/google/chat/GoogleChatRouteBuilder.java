package com.redhat.cloud.notifications.processors.google.chat;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;

@ApplicationScoped
public class GoogleChatRouteBuilder extends RouteBuilder {

    public static final String REST_PATH = API_INTERNAL + "/google-chat";
    public static final String OUTGOING_ROUTE = "google-chat-outgoing";

    private static final String DIRECT_ENDPOINT = "direct:google-chat";

    @ConfigProperty(name = "notifications.google.chat.camel.max-endpoint-cache-size", defaultValue = "100")
    int maxEndpointCacheSize;

    @Inject
    GoogleChatNotificationProcessor googleChatNotificationProcessor;

    @Inject
    NotificationErrorProcessor notificationErrorProcessor;

    @Override
    public void configure() {

        /*
         * This route exposes a REST endpoint that is used from GoogleChatProcessor to send a Google Chat notification.
         */
        rest(REST_PATH)
                .post()
                .consumes("application/json")
                .routeId("google-chat-incoming")
                .to(DIRECT_ENDPOINT);

        /*
         * This route transforms an incoming REST payload into a message that is eventually sent to Google Chat.
         */
        from(DIRECT_ENDPOINT)
                .routeId(OUTGOING_ROUTE)
                .process(googleChatNotificationProcessor)
                .removeHeaders("CamelHttp*")
                .setHeader(HTTP_METHOD, constant("POST"))
                .setHeader(CONTENT_TYPE, constant("application/json"))
                .doTry()
                    .toD(URLDecoder.decode("${exchangeProperty.webhookUrl}", StandardCharsets.UTF_8), maxEndpointCacheSize)
                .doCatch(HttpOperationFailedException.class)
                    .process(notificationErrorProcessor);
    }
}
