package com.redhat.cloud.notifications.connector.servicenow;

import com.redhat.cloud.notifications.connector.ConnectorRoutesTest;
import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Predicate;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.servicenow.ExchangeProperty.ACCOUNT_ID;
import static com.redhat.cloud.notifications.connector.servicenow.ExchangeProperty.AUTHENTICATION_TOKEN;
import static com.redhat.cloud.notifications.connector.servicenow.ExchangeProperty.TARGET_URL_NO_SCHEME;
import static com.redhat.cloud.notifications.connector.servicenow.ExchangeProperty.TRUST_ALL;
import static com.redhat.cloud.notifications.connector.servicenow.ServiceNowCloudEventDataExtractor.NOTIF_METADATA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ServiceNowConnectorRoutesTest extends ConnectorRoutesTest {

    @Override
    protected String getMockEndpointPattern() {
        return "https://foo.bar*";
    }

    @Override
    protected String getMockEndpointUri() {
        return "mock:https:foo.bar";
    }

    @Override
    protected JsonObject buildIncomingPayload(String targetUrl) {

        JsonObject metadata = new JsonObject();
        metadata.put("url", targetUrl);
        metadata.put("X-Insight-Token", "super-secret-token");
        metadata.put("trustAll", "true");

        JsonObject payload = new JsonObject();
        payload.put(NOTIF_METADATA, metadata);
        payload.put("org_id", DEFAULT_ORG_ID);
        payload.put("account_id", DEFAULT_ACCOUNT_ID);
        payload.put("events", JsonArray.of(
                JsonObject.of("event-1-key", "event-1-value"),
                JsonObject.of("event-2-key", "event-2-value")
        ));
        return payload;
    }

    @Override
    protected Predicate checkOutgoingPayload(JsonObject incomingPayload) {

        JsonObject expectedPayload = incomingPayload.copy();
        expectedPayload.remove(NOTIF_METADATA);

        return exchange -> {
            String outgoingPayload = exchange.getIn().getBody(String.class);

            assertEquals(DEFAULT_ORG_ID, exchange.getProperty(ORG_ID, String.class));
            assertEquals(DEFAULT_ACCOUNT_ID, exchange.getProperty(ACCOUNT_ID, String.class));
            assertEquals("super-secret-token", exchange.getProperty(AUTHENTICATION_TOKEN, String.class));
            assertTrue(exchange.getProperty(TRUST_ALL, Boolean.class));
            assertEquals(exchange.getProperty(TARGET_URL, String.class), "https://" + exchange.getProperty(TARGET_URL_NO_SCHEME, String.class));
            assertEquals(expectedPayload.encode(), outgoingPayload);

            // In case of assertion failure, this return value won't be used.
            return true;
        };
    }

    @Override
    protected boolean useHttps() {
        return true;
    }
}
