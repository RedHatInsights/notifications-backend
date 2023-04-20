package com.redhat.cloud.notifications.processors.slack;

import com.redhat.cloud.notifications.Json;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.processors.slack.SlackRouteBuilder.REST_PATH;
import static com.redhat.cloud.notifications.processors.slack.SlackRouteBuilder.SLACK_OUTGOING_ROUTE;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.apache.camel.builder.AdviceWith.adviceWith;

@QuarkusTest
public class SlackRouteBuilderTest extends CamelQuarkusTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testRoutes() throws Exception {
        adviceWith(SLACK_OUTGOING_ROUTE, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip("slack*");
            }
        });

        SlackNotification notification = buildNotification("https://foo.bar");

        // Camel encodes the '#' character into '%23' when building the mock endpoint URI.
        MockEndpoint slackEndpoint = getMockEndpoint("mock:slack:%23notifications");
        slackEndpoint.expectedBodiesReceived(notification.message);

        given()
                .contentType(JSON)
                .body(Json.encode(notification))
                .when().post(REST_PATH)
                .then().statusCode(200);

        slackEndpoint.assertIsSatisfied();
    }

    public static SlackNotification buildNotification(String webhookUrl) {
        SlackNotification notification = new SlackNotification();
        notification.orgId = DEFAULT_ORG_ID;
        notification.historyId = UUID.randomUUID();
        notification.webhookUrl = webhookUrl;
        notification.channel = "#notifications";
        notification.message = "This is a test!";
        return notification;
    }
}
