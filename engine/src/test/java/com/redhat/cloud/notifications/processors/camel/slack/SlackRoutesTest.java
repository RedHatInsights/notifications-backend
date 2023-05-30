package com.redhat.cloud.notifications.processors.camel.slack;

import com.redhat.cloud.notifications.processors.camel.CamelRoutesTest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import java.net.URLEncoder;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.events.EndpointProcessor.SLACK_ENDPOINT_SUBTYPE;
import static com.redhat.cloud.notifications.processors.camel.RetryCounterProcessor.CAMEL_SLACK_RETRY_COUNTER;
import static com.redhat.cloud.notifications.processors.camel.slack.SlackRouteBuilder.SLACK_ROUTE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.camel.builder.AdviceWith.adviceWith;

@QuarkusTest
@TestProfile(SlackTestProfile.class)
public class SlackRoutesTest extends CamelRoutesTest {

    @Override
    protected String getIncomingRoute() {
        return SLACK_ROUTE;
    }

    @Override
    protected String getEndpointSubtype() {
        return SLACK_ENDPOINT_SUBTYPE;
    }

    @Override
    protected String getRetryCounterName() {
        return CAMEL_SLACK_RETRY_COUNTER;
    }

    @Override
    protected Object buildNotification(String webhookUrl) {
        return buildCamelSlackNotification(webhookUrl);
    }

    public static SlackNotification buildCamelSlackNotification(String webhookUrl) {
        SlackNotification notification = new SlackNotification();
        notification.orgId = DEFAULT_ORG_ID;
        notification.webhookUrl = webhookUrl;
        notification.channel = "#notifications";
        notification.message = "This is a test!";
        return notification;
    }

    @Test
    @Override
    protected void testRoutes() throws Exception {
        String testRoutesChannel = "#test_routes_channel";
        adviceWith(getIncomingRoute(), context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip("slack:" + testRoutesChannel + "*");
            }
        });
        mockKafkaSourceEndpoint();
        MockEndpoint kafkaSinkMockEndpoint = mockKafkaSinkEndpoint();

        SlackNotification notification = buildCamelSlackNotification("https://foo.bar");
        notification.channel = testRoutesChannel;

        // Camel encodes the '#' character into '%23' when building the mock endpoint URI.
        MockEndpoint slackMockEndpoint = getMockEndpoint("mock:slack:" + URLEncoder.encode(testRoutesChannel, UTF_8));
        slackMockEndpoint.expectedBodiesReceived(notification.message);

        String cloudEventId = sendMessageToKafkaSource(notification, SLACK_ENDPOINT_SUBTYPE);

        slackMockEndpoint.assertIsSatisfied();
        assertKafkaSinkIsSatisfied(cloudEventId, notification, kafkaSinkMockEndpoint, true, "Event " + cloudEventId + " sent successfully");
    }
}
