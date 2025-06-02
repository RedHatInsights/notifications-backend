package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.ConnectorRoutesTest;
import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Predicate;
import org.junit.jupiter.api.Test;
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

    private final String EXPECTED_MESSAGE = "{\"blocks\": [{\"type\": \"section\",\"text\": {\"type\": \"mrkdwn\",\"text\": \"*Bug Fixes - Errata - Subscription Services*\"}}]}";

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
        payload.put("message", EXPECTED_MESSAGE);
        return payload;
    }

    @Override
    protected Predicate checkOutgoingPayload(JsonObject incomingPayload) {
        return exchange -> {
            JsonObject outgoingPayload = new JsonObject(exchange.getIn().getBody(String.class));
            System.out.println(outgoingPayload);
            boolean textMessageMatch = outgoingPayload.getString("blocks").equals(new JsonObject(EXPECTED_MESSAGE).getString("blocks"));
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
