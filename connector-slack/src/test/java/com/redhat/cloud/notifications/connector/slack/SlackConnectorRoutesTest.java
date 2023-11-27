package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.ConnectorRoutesTest;
import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Predicate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.util.UUID;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;
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
    protected String getMockEndpointPattern() {
        return "slack:" + SLACK_CHANNEL + "*";
    }

    @Override
    protected String getMockEndpointUri() {
        return "mock:slack:" + URLEncoder.encode(SLACK_CHANNEL, UTF_8);
    }

    @Override
    protected JsonObject buildIncomingPayload(String targetUrl) {
        JsonObject payload = new JsonObject();
        payload.put("orgId", DEFAULT_ORG_ID);
        payload.put("webhookUrl", targetUrl);
        payload.put("channel", SLACK_CHANNEL);
        payload.put("message", "This is a test!");
        return payload;
    }

    @Override
    protected Predicate checkOutgoingPayload(JsonObject incomingPayload) {
        return exchange -> {
            String outgoingPayload = exchange.getIn().getBody(String.class);
            return outgoingPayload.equals(incomingPayload.getString("message"));
        };
    }

    // See ConnectorRoutesTest#isConnectorRouteFailureHandled().
    @Override
    protected boolean isConnectorRouteFailureHandled() {
        return false;
    }

    @Test
    void test404ChannelNotFound() throws Exception {

        mockKafkaSourceEndpoint(); // This is the entry point of the connector.
        mock404ChannelNotFound();
        MockEndpoint kafkaSinkMockEndpoint = mockKafkaSinkEndpoint(); // This is where the return message to the engine is sent.

        JsonObject incomingPayload = buildIncomingPayload(getMockServerUrl());

        String cloudEventId = sendMessageToKafkaSource(incomingPayload);

        assertKafkaSinkIsSatisfied(cloudEventId, kafkaSinkMockEndpoint, false, getMockServerUrl(), "Error POSTing to Slack API");

        checkRouteMetrics(ENGINE_TO_CONNECTOR, 1, 1, 1);
        checkRouteMetrics(connectorConfig.getConnectorName(), 0, 0, 1);
        checkRouteMetrics(SUCCESS, 0, 0, 0);
        checkRouteMetrics(CONNECTOR_TO_ENGINE, 0, 1, 1);
        micrometerAssertionHelper.assertCounterIncrement(connectorConfig.getRedeliveryCounterName(), 0);
    }

    @Override
    @Test
    protected void testFailedNotificationError500() throws Exception {
        mockRemoteServerError(500, "My custom internal error");

        // We do not expect any redeliveries because the Slack connector does
        // not depend on the "HTTP common" module.
        testFailedNotification(0);
    }

    private void mock404ChannelNotFound() {
        getClient()
                .when(request().withMethod("POST"))
                .respond(response().withStatusCode(404).withBody("channel_not_found"));
    }
}
