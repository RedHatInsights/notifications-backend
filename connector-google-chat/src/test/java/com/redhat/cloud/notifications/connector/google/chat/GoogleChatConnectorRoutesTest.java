package com.redhat.cloud.notifications.connector.google.chat;

import com.redhat.cloud.notifications.connector.ConnectorRoutesTest;
import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class GoogleChatConnectorRoutesTest extends ConnectorRoutesTest {

    @Override
    protected String getOriginalEndpointPattern() {
        return "https://foo.bar";
    }

    @Override
    protected String getMockEndpointUri() {
        return "mock:https:foo.bar";
    }

    @Override
    protected JsonObject buildNotification(String targetUrl) {
        JsonObject notification = new JsonObject();
        notification.put("orgId", DEFAULT_ORG_ID);
        notification.put("webhookUrl", targetUrl);
        notification.put("message", "This is a test!");
        return notification;
    }
}
