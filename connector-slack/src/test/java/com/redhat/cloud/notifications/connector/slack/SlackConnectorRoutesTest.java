package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.ConnectorRoutesTest;
import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;

import java.net.URLEncoder;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static java.nio.charset.StandardCharsets.UTF_8;

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
}
