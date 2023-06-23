package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.ConnectorRoutesTest;
import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.util.UUID;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.CONNECTOR_TO_ENGINE;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.ENGINE_TO_CONNECTOR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class SlackConnectorRoutesTest extends ConnectorRoutesTest {

    // The randomness of this field helps avoid side-effects between tests.
    private String SLACK_CHANNEL = "#notifications-" + UUID.randomUUID();

    @Override
    protected String getOriginalEndpointPattern() {
        return "slack:" + SLACK_CHANNEL + "*";
    }

    @Override
    protected String getMockEndpointUri() {
        return "mock:slack:" + URLEncoder.encode(SLACK_CHANNEL, UTF_8);
    }

    @Override
    protected JsonObject buildNotification(String targetUrl) {
        JsonObject notification = new JsonObject();
        notification.put("orgId", DEFAULT_ORG_ID);
        notification.put("webhookUrl", targetUrl);
        notification.put("channel", SLACK_CHANNEL);
        notification.put("message", "This is a test!");
        return notification;
    }

    // See ConnectorRoutesTest#isConnectorRouteFailureHandled().
    @Override
    protected boolean isConnectorRouteFailureHandled() {
        return false;
    }

    @Test
    void test404ChannelNotFound() throws Exception {

        mockKafkaSourceEndpoint(); // This is the entry point of the connector.
        String remoteServerPath = mock404ChannelNotFound();
        MockEndpoint kafkaSinkMockEndpoint = mockKafkaSinkEndpoint(); // This is where the return message to the engine is sent.

        JsonObject notification = buildNotification(remoteServerPath);

        String cloudEventId = sendMessageToKafkaSource(notification);

        assertKafkaSinkIsSatisfied(cloudEventId, notification, kafkaSinkMockEndpoint, false, "Error POSTing to Slack API");

        checkRouteMetrics(ENGINE_TO_CONNECTOR, 1, 1, 1);
        checkRouteMetrics(connectorConfig.getConnectorName(), 0, 0, 1);
        checkRouteMetrics(SUCCESS, 0, 0, 0);
        checkRouteMetrics(CONNECTOR_TO_ENGINE, 0, 1, 1);
        micrometerAssertionHelper.assertCounterIncrement(connectorConfig.getRedeliveryCounterName(), 0);
    }

    private String mock404ChannelNotFound() {
        getClient()
                .when(request().withMethod("POST").withPath(REMOTE_SERVER_PATH))
                .respond(response().withStatusCode(404).withBody("channel_not_found"));
        return getMockServerUrl() + REMOTE_SERVER_PATH;
    }
}
