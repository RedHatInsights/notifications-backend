package com.redhat.cloud.notifications.connector.v2;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

/**
 * Test to verify the new integration test framework works
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class TestConnectorIntegrationTest extends BaseConnectorIntegrationTest {

    @Override
    protected JsonObject buildIncomingPayload(String targetUrl) {
        JsonObject payload = new JsonObject();
        payload.put("test", "data");
        payload.put("targetUrl", targetUrl);
        return payload;
    }

    @Override
    protected void assertOutgoingPayload(JsonObject incomingPayload, JsonObject outgoingPayload) {
        // Test-specific payload verification can be added here
    }

    @Override
    protected String getConnectorSpecificTargetUrl() {
        return getMockServerUrl() + "/test";
    }

    @Test
    void testIntegrationTestFrameworkComponents() {
        // Test that all the integration test framework components are available
        assert incomingMessageSource != null;
        assert outgoingMessageSink != null;
        assert connectorConfig != null;
        assert micrometerAssertionHelper != null;

        // Test that we can create test data
        JsonObject testPayload = buildIncomingPayload(getConnectorSpecificTargetUrl());
        assert testPayload != null;
        assert testPayload.getString("test").equals("data");

        // Test that we can create a MessageContext
        MessageContext context = createTestMessageContext(testPayload);
        assert context != null;
        //assert context.getBody() != null;
        assert context.getHeaders() != null;

        // Test metrics saving functionality
        saveConnectorMetrics(); // Should not throw

        // Test MockServer URL generation
        String mockUrl = getMockServerUrl();
        assert mockUrl != null && mockUrl.startsWith("http");
    }
}
