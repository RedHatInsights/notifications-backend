package com.redhat.cloud.notifications.connector.microsoft.teams;

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

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TeamsConnectorRoutesTest extends ConnectorRoutesTest {

    @InjectSpy
    TemplateService templateService;

    private boolean testWithEventDataMap = false;

    @Override
    protected String getMockEndpointPattern() {
        return "https://foo.bar";
    }

    @Override
    protected String getMockEndpointUri() {
        return "mock:https:foo.bar";
    }

    private static final String EXPECTED_MESSAGE = "{\n" +
        "  \"type\":\"message\",\n" +
        "  \"attachments\":[\n" +
        "    {\n" +
        "      \"contentType\":\"application/vnd.microsoft.card.adaptive\",\n" +
        "      \"contentUrl\":null,\n" +
        "      \"content\":{\n" +
        "        \"$schema\":\"http://adaptivecards.io/schemas/adaptive-card.json\",\n" +
        "        \"type\":\"AdaptiveCard\",\n" +
        "        \"version\":\"1.5\",\n" +
        "        \"body\":[\n" +
        "          {\n" +
        "            \"type\": \"TextBlock\",\n" +
        "            \"text\": \"[my-computer](https://localhost/insights/inventory/6ad30f3e-0497-4e74-99f1-b3f9a6120a6f?from=notifications&integration=teams) triggered 1 event from Policies - Red Hat Enterprise Linux. [Open Policies](https://localhost/insights/policies?from=notifications&integration=teams)\",\n" +
        "            \"wrap\": true\n" +
        "          }\n" +
        "        ],\n" +
        "        \"msteams\": {\n" +
        "          \"width\": \"Full\"\n" +
        "        }\n" +
        "      }\n" +
        "    }\n" +
        "  ]\n" +
        "}";

    @Override
    protected JsonObject buildIncomingPayload(String targetUrl) {
        JsonObject payload = new JsonObject();
        payload.put("orgId", DEFAULT_ORG_ID);
        payload.put("webhookUrl", targetUrl);
        payload.put("message", EXPECTED_MESSAGE);
        if (testWithEventDataMap) {
            payload.put("eventData", getDefaultEventDataMap());
        }
        return payload;
    }

    @Override
    protected Predicate checkOutgoingPayload(JsonObject incomingPayload) {
        return exchange -> {
            String outgoingPayload = exchange.getIn().getBody(String.class);

            if (testWithEventDataMap) {
                verify(templateService, times(1)).renderTemplate(any(TemplateDefinition.class), anyMap());
            } else {
                verifyNoInteractions(templateService);
            }
            return outgoingPayload.equals(incomingPayload.getString("message"));
        };
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
