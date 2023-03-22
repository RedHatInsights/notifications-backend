package com.redhat.cloud.notifications.processors.teams;

import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;

@ApplicationScoped
public class TeamsRouteBuilder extends RouteBuilder {

    public static final String REST_PATH = API_INTERNAL + "/teams";
    public static final String TEAMS_OUTGOING_ROUTE = "teams-outgoing";

    private static final String TEAMS_DIRECT_ENDPOINT = "direct:teams";

    @ConfigProperty(name = "notifications.teams.camel.max-endpoint-cache-size", defaultValue = "100")
    int maxEndpointCacheSize;

    @Inject
    TeamsNotificationProcessor teamsNotificationProcessor;

    @Override
    public void configure() {

        /*
         * This route exposes a REST endpoint that is used from TeamsProcessor to send a Teams notification.
         */
        rest(REST_PATH)
                .post()
                .consumes("application/json")
                .routeId("teams-incoming")
                .to(TEAMS_DIRECT_ENDPOINT);

        /*
         * This route transforms an incoming REST payload into a message that is eventually sent to Teams.
         */
        from(TEAMS_DIRECT_ENDPOINT)
                .routeId(TEAMS_OUTGOING_ROUTE)
                .process(teamsNotificationProcessor)
                .removeHeaders("CamelHttp*")
                .setHeader(HTTP_METHOD, constant("POST"))
                .setHeader(CONTENT_TYPE, constant("application/json"))
                .toD("${exchangeProperty.webhookUrl}", maxEndpointCacheSize);
    }
}
