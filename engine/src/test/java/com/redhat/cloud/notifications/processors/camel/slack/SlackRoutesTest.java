package com.redhat.cloud.notifications.processors.camel.slack;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.processors.camel.CamelRoutesTest;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.URLEncoder;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.processors.camel.RetryCounterProcessor.CAMEL_SLACK_RETRY_COUNTER;
import static com.redhat.cloud.notifications.processors.camel.slack.SlackRouteBuilder.REST_PATH;
import static com.redhat.cloud.notifications.processors.camel.slack.SlackRouteBuilder.SLACK_INCOMING_ROUTE;
import static com.redhat.cloud.notifications.processors.camel.slack.SlackRouteBuilder.SLACK_OUTGOING_ROUTE;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.camel.builder.AdviceWith.adviceWith;

@QuarkusTest
public class SlackRoutesTest extends CamelRoutesTest {

    @BeforeEach
    void beforeTest() {
        restPath = SlackRouteBuilder.REST_PATH;
        mockPath = "/camel/slack";
        mockPathKo = "/camel/slack_ko";
        mockPathRetries = "/camel/slack_retries";

        camelIncomingRouteName = SLACK_INCOMING_ROUTE;
        camelOutgoingRouteName = SLACK_OUTGOING_ROUTE;
        retryCounterName = CAMEL_SLACK_RETRY_COUNTER;
    }

    @Override
    protected Object buildNotification(String webhookUrl) {
        return buildCamelSlackNotification(webhookUrl);
    }

    public static SlackNotification buildCamelSlackNotification(String webhookUrl) {
        SlackNotification notification = new SlackNotification();
        notification.orgId = DEFAULT_ORG_ID;
        notification.historyId = UUID.randomUUID();
        notification.webhookUrl = webhookUrl;
        notification.channel = "#notifications";
        notification.message = "This is a test!";
        return notification;
    }

    @Test
    @Override
    protected void testRoutes() throws Exception {
        String testRoutesChannel = "#test_routes_channel";
        adviceWith(SLACK_OUTGOING_ROUTE, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip("slack:" + testRoutesChannel + "*");
            }
        });
        MockEndpoint kafkaEndpoint = mockKafkaEndpoint();

        SlackNotification notification = buildCamelSlackNotification("https://foo.bar");
        notification.channel = testRoutesChannel;

        // Camel encodes the '#' character into '%23' when building the mock endpoint URI.
        MockEndpoint slackEndpoint = getMockEndpoint("mock:slack:" + URLEncoder.encode(testRoutesChannel, UTF_8));
        slackEndpoint.expectedBodiesReceived(notification.message);

        given()
            .contentType(JSON)
            .body(Json.encode(notification))
            .when().post(REST_PATH)
            .then().statusCode(200);

        slackEndpoint.assertIsSatisfied();
        assertKafkaIsSatisfied(notification, kafkaEndpoint, true, "Event " + notification.historyId + " sent successfully");
    }
}
