package com.redhat.cloud.notifications.connector.splunk;

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
import static com.redhat.cloud.notifications.connector.splunk.ExchangeProperty.ACCOUNT_ID;
import static com.redhat.cloud.notifications.connector.splunk.ExchangeProperty.AUTHENTICATION_TOKEN;
import static com.redhat.cloud.notifications.connector.splunk.ExchangeProperty.TARGET_URL_NO_SCHEME;
import static com.redhat.cloud.notifications.connector.splunk.ExchangeProperty.TRUST_ALL;
import static com.redhat.cloud.notifications.connector.splunk.SplunkCloudEventDataExtractor.NOTIF_METADATA;
import static com.redhat.cloud.notifications.connector.splunk.SplunkCloudEventDataExtractor.SERVICES_COLLECTOR_EVENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class SplunkConnectorRoutesTest extends ConnectorRoutesTest {

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
                JsonObject.of("event-2-key", "event-2-value"),
                JsonObject.of("event-3-key", "event-3-value")
        ));
        return payload;
    }

    @Override
    protected Predicate checkOutgoingPayload(JsonObject incomingPayload) {
        return exchange -> {
            String outgoingPayload = exchange.getIn().getBody(String.class);

            assertEquals(DEFAULT_ORG_ID, exchange.getProperty(ORG_ID, String.class));
            assertEquals(DEFAULT_ACCOUNT_ID, exchange.getProperty(ACCOUNT_ID, String.class));
            assertEquals("super-secret-token", exchange.getProperty(AUTHENTICATION_TOKEN, String.class));
            assertTrue(exchange.getProperty(TRUST_ALL, Boolean.class));
            assertNotNull(exchange.getProperty(TARGET_URL, String.class));
            assertNotNull(exchange.getProperty(TARGET_URL_NO_SCHEME, String.class));
            assertTrue(exchange.getProperty(TARGET_URL, String.class).endsWith("/services/collector/event"));
            assertEquals(exchange.getProperty(TARGET_URL, String.class), "https://" + exchange.getProperty(TARGET_URL_NO_SCHEME, String.class));

            JsonObject event1 = buildSplitEvent("event-1-key", "event-1-value");
            JsonObject event2 = buildSplitEvent("event-2-key", "event-2-value");
            JsonObject event3 = buildSplitEvent("event-3-key", "event-3-value");
            assertEquals(outgoingPayload, event1.encode() + event2.encode() + event3.encode());

            // In case of assertion failure, this return value won't be used.
            return true;
        };
    }

    @Override
    protected boolean useHttps() {
        return true;
    }

    @Override
    protected String getRemoteServerPath() {
        return SERVICES_COLLECTOR_EVENT;
    }

    private static JsonObject buildSplitEvent(String key, String value) {
        return JsonObject.of(
                "source", "eventing",
                "sourcetype", "Insights event",
                "event", JsonObject.of(
                        "org_id", DEFAULT_ORG_ID,
                        "account_id", DEFAULT_ACCOUNT_ID,
                        "events", JsonArray.of(
                                JsonObject.of(key, value)
                        )
                )
        );
    }
}
