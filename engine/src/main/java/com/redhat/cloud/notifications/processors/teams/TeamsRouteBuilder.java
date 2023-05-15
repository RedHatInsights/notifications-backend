package com.redhat.cloud.notifications.processors.teams;

import com.redhat.cloud.notifications.processors.camel.HttpOperationFailedExceptionProcessor;
import com.redhat.cloud.notifications.processors.camel.RetryCounterProcessor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;


import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.models.HttpType.POST;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.LoggingLevel.INFO;

@ApplicationScoped
public class TeamsRouteBuilder extends RouteBuilder {

    public static final String REST_PATH = API_INTERNAL + "/teams";
    public static final String TEAMS_OUTGOING_ROUTE = "teams-outgoing";

    public static final String TEAMS_INCOMING_ROUTE = "teams-incoming";

    private static final String TEAMS_DIRECT_ENDPOINT = "direct:teams";

    @ConfigProperty(name = "notifications.teams.camel.max-endpoint-cache-size", defaultValue = "100")
    int maxEndpointCacheSize;

    @ConfigProperty(name = "notifications.slack.camel.maximum-redeliveries", defaultValue = "2")
    int maxRedeliveries;

    @ConfigProperty(name = "notifications.slack.camel.redelivery-delay", defaultValue = "1000")
    long redeliveryDelay;

    @Inject
    RetryCounterProcessor retryCounterProcessor;

    @Inject
    HttpOperationFailedExceptionProcessor notificationErrorProcessor;

    @Inject
    TeamsNotificationProcessor teamsNotificationProcessor;

    @Override
    public void configure() {

        onException(IOException.class)
            .maximumRedeliveries(maxRedeliveries)
            .redeliveryDelay(redeliveryDelay)
            .onRedelivery(retryCounterProcessor)
            .retryAttemptedLogLevel(INFO);

        onException(HttpOperationFailedException.class)
            .process(notificationErrorProcessor);

        /*
         * This route exposes a REST endpoint that is used from TeamsProcessor to send a Teams notification.
         */
        rest(REST_PATH)
                .post()
                .consumes(APPLICATION_JSON)
                .routeId(TEAMS_INCOMING_ROUTE)
                .to(TEAMS_DIRECT_ENDPOINT);

        /*
         * This route transforms an incoming REST payload into a message that is eventually sent to Teams.
         */
        from(TEAMS_DIRECT_ENDPOINT)
                .routeId(TEAMS_OUTGOING_ROUTE)
                .process(teamsNotificationProcessor)
                .removeHeaders("CamelHttp*")
                .setHeader(HTTP_METHOD, constant(POST))
                .setHeader(CONTENT_TYPE, constant(APPLICATION_JSON))
                .toD("${exchangeProperty.webhookUrl}", maxEndpointCacheSize);
    }
}
