package com.redhat.cloud.notifications.connector.microsoft.teams;

import com.redhat.cloud.notifications.connector.ConnectorRoutesTest;
import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Predicate;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TeamsConnectorRoutesTest extends ConnectorRoutesTest {

    @Override
    protected String getMockEndpointPattern() {
        return "https://foo.bar";
    }

    @Override
    protected String getMockEndpointUri() {
        return "mock:https:foo.bar";
    }

    @Override
    protected JsonObject buildIncomingPayload(String targetUrl) {
        JsonObject payload = new JsonObject();
        payload.put("orgId", DEFAULT_ORG_ID);
        payload.put("webhookUrl", targetUrl);
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
}
