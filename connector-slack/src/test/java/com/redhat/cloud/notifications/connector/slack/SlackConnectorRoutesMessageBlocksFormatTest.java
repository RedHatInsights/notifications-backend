package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.connector.ConnectorRoutesTest;
import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import com.redhat.cloud.notifications.qute.templates.mapping.SubscriptionServices;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Predicate;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class SlackConnectorRoutesMessageBlocksFormatTest extends ConnectorRoutesTest {

    // The randomness of this field helps avoid side-effects between tests.
    private String SLACK_CHANNEL = "#notifications-" + UUID.randomUUID();

    @Override
    protected String getMockEndpointPattern() {
        return "https://foo.bar";
    }

    @Override
    protected String getMockEndpointUri() {
        return "mock:https:foo.bar";
    }

    private boolean testWithoutChannel = false;

    @Override
    protected JsonObject buildIncomingPayload(String targetUrl) {
        JsonObject payload = new JsonObject();
        payload.put("orgId", DEFAULT_ORG_ID);
        payload.put("webhookUrl", targetUrl);
        if (!testWithoutChannel) {
            payload.put("channel", SLACK_CHANNEL);
        } else {
            payload.put("channel", null);
        }

        Map<String, Object> source = new HashMap<>();
        source.put("event_type", Map.of("display_name", SubscriptionServices.ERRATA_NEW_SUBSCRIPTION_BUGFIX_ERRATA));
        source.put("application", Map.of("display_name", SubscriptionServices.ERRATA_APP_NAME));
        source.put("bundle", Map.of("display_name", SubscriptionServices.BUNDLE_NAME));

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("bundle", SubscriptionServices.BUNDLE_NAME);
        eventData.put("application", SubscriptionServices.ERRATA_APP_NAME);
        eventData.put("event_type", SubscriptionServices.ERRATA_NEW_SUBSCRIPTION_BUGFIX_ERRATA);
        eventData.put("events", new ArrayList<>());
        eventData.put("environment", Map.of("url", new ArrayList<>()));
        eventData.put("orgId", TestConstants.DEFAULT_ORG_ID);
        eventData.put("source", source);

        payload.put("eventData", eventData);
        return payload;
    }

    @Override
    protected Predicate checkOutgoingPayload(JsonObject incomingPayload) {
        return exchange -> {
            JsonObject outgoingPayload = new JsonObject(exchange.getIn().getBody(String.class));
            boolean textMessageMatch = !outgoingPayload.getString("blocks").isEmpty();
            if (!testWithoutChannel) {
                return textMessageMatch && outgoingPayload.getString(ExchangeProperty.CHANNEL).equals(incomingPayload.getString(ExchangeProperty.CHANNEL));
            }
            return textMessageMatch && !outgoingPayload.containsKey(ExchangeProperty.CHANNEL);
        };
    }

    @Test
    protected void testSuccessfulNotificationWithoutChannel() throws Exception {
        try {
            testWithoutChannel = true;
            testSuccessfulNotification();
        } finally {
            testWithoutChannel = false;
        }
    }
}
