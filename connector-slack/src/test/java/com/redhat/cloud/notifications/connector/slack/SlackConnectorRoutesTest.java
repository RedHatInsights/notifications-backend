package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.ConnectorRoutesTest;
import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Predicate;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class SlackConnectorRoutesTest extends ConnectorRoutesTest {

    @InjectSpy
    TemplateService templateService;

    private boolean testWithEventDataMap = false;

    private static final String EXPECTED_MESSAGE = "<https://localhost/insights/inventory/6ad30f3e-0497-4e74-99f1-b3f9a6120a6f?from=notifications&integration=teams|my-computer> triggered 1 event from Policies - Red Hat Enterprise Linux. <https://localhost/insights/policies?from=notifications&integration=teams|Open Policies>";

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

        if (testWithEventDataMap) {
            payload.put("eventData", getDefaultEventDataMap());
        }

        payload.put("message", EXPECTED_MESSAGE);
        return payload;
    }

    @Override
    protected Predicate checkOutgoingPayload(JsonObject incomingPayload) {
        return exchange -> {
            JsonObject outgoingPayload = new JsonObject(exchange.getIn().getBody(String.class));
            if (testWithEventDataMap) {
                verify(templateService, times(1)).renderTemplate(any(TemplateDefinition.class), anyMap());
            } else {
                verifyNoInteractions(templateService);
            }

            boolean textMessageMatch = outgoingPayload.getString("text").equals(incomingPayload.getString("message"));
            if (!testWithoutChannel) {
                return textMessageMatch && outgoingPayload.getString(ExchangeProperty.CHANNEL).equals(incomingPayload.getString(ExchangeProperty.CHANNEL));
            }
            return textMessageMatch && !outgoingPayload.containsKey(ExchangeProperty.CHANNEL);
        };
    }

    @Test
    protected void testSuccessfulNotificationWithChannel() throws Exception {
        try {
            testWithoutChannel = true;
            testSuccessfulNotification();
        } finally {
            testWithoutChannel = false;
        }
    }

    @Test
    protected void testSuccessfulNotificationWithEventDataMap() throws Exception {
        try {
            testWithEventDataMap = true;
            testSuccessfulNotification();
        } finally {
            testWithEventDataMap = false;
        }
    }
}
