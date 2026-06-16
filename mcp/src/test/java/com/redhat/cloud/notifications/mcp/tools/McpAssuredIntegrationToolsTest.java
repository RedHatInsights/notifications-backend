package com.redhat.cloud.notifications.mcp.tools;

import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.mcp.McpAssuredTestBase;
import com.redhat.cloud.notifications.mcp.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class McpAssuredIntegrationToolsTest extends McpAssuredTestBase {

    private static final String INTEGRATION_ID = "12345678-abcd-1234-abcd-1234567890ab";
    private static final String HISTORY_ID = "abcd1234-abcd-1234-abcd-1234567890ab";
    private static final String EVENT_TYPE_ID = "87654321-dcba-4321-dcba-ba0987654321";

    @Test
    public void testListToolsContainsIntegrationTools() {
        client.when()
                .toolsList(page -> {
                    assertNotNull(page.findByName("getIntegrations"));
                    assertNotNull(page.findByName("getIntegration"));
                    assertNotNull(page.findByName("getIntegrationHistory"));
                    assertNotNull(page.findByName("getIntegrationHistoryDetails"));
                    assertNotNull(page.findByName("createIntegration"));
                    assertNotNull(page.findByName("updateIntegration"));
                    assertNotNull(page.findByName("deleteIntegration"));
                    assertNotNull(page.findByName("enableIntegration"));
                    assertNotNull(page.findByName("disableIntegration"));
                    assertNotNull(page.findByName("testIntegration"));
                    assertNotNull(page.findByName("addEventTypeToIntegration"));
                    assertNotNull(page.findByName("deleteEventTypeFromIntegration"));
                    assertNotNull(page.findByName("updateEventTypesLinkedToIntegration"));
                })
                .thenAssertResults();
    }

    // --- getIntegrations ---

    @Test
    public void testGetIntegrations() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v2.0/endpoints"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"data\":[{\"id\":\"" + INTEGRATION_ID + "\",\"name\":\"My Webhook\",\"type\":\"webhook\"}],\"meta\":{\"count\":1},\"links\":{}}"))
        );

        client.when()
                .toolsCall("getIntegrations", Map.of(), response -> {
                    assertFalse(response.isError());
                    String text = response.firstContent().asText().text();
                    assertTrue(text.contains("My Webhook"));
                    assertTrue(text.contains("webhook"));
                })
                .thenAssertResults();
    }

    @Test
    public void testGetIntegrationsBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v2.0/endpoints"))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("getIntegrations", Map.of(), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                })
                .thenAssertResults();
    }

    // --- getIntegration ---

    @Test
    public void testGetIntegration() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v2.0/endpoints/" + INTEGRATION_ID))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"id\":\"" + INTEGRATION_ID + "\",\"name\":\"My Webhook\",\"type\":\"webhook\",\"enabled\":true}"))
        );

        client.when()
                .toolsCall("getIntegration", Map.of("id", INTEGRATION_ID), response -> {
                    assertFalse(response.isError());
                    String text = response.firstContent().asText().text();
                    assertTrue(text.contains("My Webhook"));
                    assertTrue(text.contains(INTEGRATION_ID));
                })
                .thenAssertResults();
    }

    @Test
    public void testGetIntegrationBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v2.0/endpoints/" + INTEGRATION_ID))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("getIntegration", Map.of("id", INTEGRATION_ID), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                })
                .thenAssertResults();
    }

    // --- getIntegrationHistory ---

    @Test
    public void testGetIntegrationHistory() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v2.0/endpoints/" + INTEGRATION_ID + "/history"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"data\":[{\"id\":\"" + HISTORY_ID + "\",\"status\":\"SUCCESS\"}],\"meta\":{\"count\":1},\"links\":{}}"))
        );

        client.when()
                .toolsCall("getIntegrationHistory", Map.of("id", INTEGRATION_ID), response -> {
                    assertFalse(response.isError());
                    String text = response.firstContent().asText().text();
                    assertTrue(text.contains("SUCCESS"));
                    assertTrue(text.contains("abcd1234"));
                })
                .thenAssertResults();
    }

    @Test
    public void testGetIntegrationHistoryBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v2.0/endpoints/" + INTEGRATION_ID + "/history"))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("getIntegrationHistory", Map.of("id", INTEGRATION_ID), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                })
                .thenAssertResults();
    }

    // --- getIntegrationHistoryDetails ---

    @Test
    public void testGetIntegrationHistoryDetails() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/history/" + HISTORY_ID + "/details"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"type\":\"com.redhat.console.notification.toCamel.webhook\",\"target\":\"https://example.com/hook\",\"outcome\":\"SUCCESS\"}"))
        );

        client.when()
                .toolsCall("getIntegrationHistoryDetails",
                        Map.of("integrationId", INTEGRATION_ID, "historyId", HISTORY_ID), response -> {
                            assertFalse(response.isError());
                            String text = response.firstContent().asText().text();
                            assertTrue(text.contains("webhook"));
                            assertTrue(text.contains("SUCCESS"));
                        })
                .thenAssertResults();
    }

    @Test
    public void testGetIntegrationHistoryDetailsBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/history/" + HISTORY_ID + "/details"))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("getIntegrationHistoryDetails",
                        Map.of("integrationId", INTEGRATION_ID, "historyId", HISTORY_ID), response -> {
                            assertTrue(response.isError());
                            assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                        })
                .thenAssertResults();
    }

    // --- enableIntegration ---

    @Test
    public void testEnableIntegration() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/enable"))
                        .willReturn(aResponse().withStatus(200))
        );

        client.when()
                .toolsCall("enableIntegration", Map.of("id", INTEGRATION_ID), response -> {
                    assertFalse(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("enabled successfully"));
                })
                .thenAssertResults();
    }

    @Test
    public void testEnableIntegrationBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/enable"))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("enableIntegration", Map.of("id", INTEGRATION_ID), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                })
                .thenAssertResults();
    }

    @Test
    public void testEnableIntegrationBackendReturns403() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/enable"))
                        .willReturn(aResponse().withStatus(403))
        );

        client.when()
                .toolsCall("enableIntegration", Map.of("id", INTEGRATION_ID), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Access denied"));
                })
                .thenAssertResults();
    }

    // --- disableIntegration ---

    @Test
    public void testDisableIntegration() {
        MockServerLifecycleManager.getClient().stubFor(
                delete(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/enable"))
                        .willReturn(aResponse().withStatus(204))
        );

        client.when()
                .toolsCall("disableIntegration", Map.of("id", INTEGRATION_ID), response -> {
                    assertFalse(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("disabled successfully"));
                })
                .thenAssertResults();
    }

    @Test
    public void testDisableIntegrationBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                delete(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/enable"))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("disableIntegration", Map.of("id", INTEGRATION_ID), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                })
                .thenAssertResults();
    }

    @Test
    public void testDisableIntegrationBackendReturns403() {
        MockServerLifecycleManager.getClient().stubFor(
                delete(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/enable"))
                        .willReturn(aResponse().withStatus(403))
        );

        client.when()
                .toolsCall("disableIntegration", Map.of("id", INTEGRATION_ID), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Access denied"));
                })
                .thenAssertResults();
    }

    // --- testIntegration ---

    @Test
    public void testTestIntegration() {
        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/test"))
                        .willReturn(aResponse().withStatus(204))
        );

        client.when()
                .toolsCall("testIntegration", Map.of("uuid", INTEGRATION_ID), response -> {
                    assertFalse(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Test notification sent successfully"));
                })
                .thenAssertResults();
    }

    @Test
    public void testTestIntegrationBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/test"))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("testIntegration", Map.of("uuid", INTEGRATION_ID), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                })
                .thenAssertResults();
    }

    @Test
    public void testTestIntegrationWithCustomMessage() {
        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/test"))
                        .willReturn(aResponse().withStatus(204))
        );

        client.when()
                .toolsCall("testIntegration",
                        Map.of("uuid", INTEGRATION_ID,
                                "requestBody", Map.of("message", "Custom test message")),
                        response -> {
                            assertFalse(response.isError());
                            assertTrue(response.firstContent().asText().text().contains("Test notification sent successfully"));
                        })
                .thenAssertResults();

        MockServerLifecycleManager.getClient().verify(
                postRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/test"))
                        .withRequestBody(matchingJsonPath("$.message", equalTo("Custom test message")))
        );
    }

    @Test
    public void testTestIntegrationWithBlankMessageReturnsValidationError() {
        client.when()
                .toolsCall("testIntegration",
                        Map.of("uuid", INTEGRATION_ID,
                                "requestBody", Map.of("message", "   ")),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(response.firstContent().asText().text().contains("must not be blank"));
                        })
                .thenAssertResults();
    }

    // --- deleteIntegration ---

    @Test
    public void testDeleteIntegration() {
        MockServerLifecycleManager.getClient().stubFor(
                delete(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID))
                        .willReturn(aResponse().withStatus(204))
        );

        client.when()
                .toolsCall("deleteIntegration", Map.of("id", INTEGRATION_ID), response -> {
                    assertFalse(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("deleted successfully"));
                })
                .thenAssertResults();

        MockServerLifecycleManager.getClient().verify(
                deleteRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID))
        );
    }

    @Test
    public void testDeleteIntegrationBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                delete(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("deleteIntegration", Map.of("id", INTEGRATION_ID), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                })
                .thenAssertResults();
    }

    @Test
    public void testDeleteIntegrationBackendReturns403() {
        MockServerLifecycleManager.getClient().stubFor(
                delete(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID))
                        .willReturn(aResponse().withStatus(403))
        );

        client.when()
                .toolsCall("deleteIntegration", Map.of("id", INTEGRATION_ID), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Access denied"));
                })
                .thenAssertResults();
    }

    // --- createIntegration ---

    @Test
    public void testCreateWebhookIntegration() {
        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"id\":\"" + INTEGRATION_ID + "\",\"name\":\"Test Webhook\",\"type\":\"webhook\"}"))
        );

        Map<String, Object> endpoint = Map.of(
                "name", "Test Webhook",
                "description", "Test webhook integration",
                "type", "webhook",
                "enabled", true,
                "properties", Map.of(
                        "url", "https://example.com/webhook",
                        "method", "POST",
                        "disable_ssl_verification", false
                )
        );

        client.when()
                .toolsCall("createIntegration", Map.of("endpoint", endpoint), response -> {
                    assertFalse(response.isError());
                    String text = response.firstContent().asText().text();
                    assertTrue(text.contains("Test Webhook"));
                    assertTrue(text.contains("webhook"));
                })
                .thenAssertResults();

        MockServerLifecycleManager.getClient().verify(
                postRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .withRequestBody(matchingJsonPath("$.type", equalTo("webhook")))
                        .withRequestBody(matchingJsonPath("$.properties.url", equalTo("https://example.com/webhook")))
        );
    }

    @Test
    public void testCreateIntegrationBackendReturns400() {
        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .willReturn(aResponse().withStatus(400))
        );

        Map<String, Object> endpoint = Map.of(
                "name", "Test Webhook", "description", "Test webhook", "type", "webhook", "enabled", true,
                "properties", Map.of("url", "https://example.com/webhook", "method", "POST", "disable_ssl_verification", false)
        );

        client.when()
                .toolsCall("createIntegration", Map.of("endpoint", endpoint), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Invalid request"));
                })
                .thenAssertResults();
    }

    @Test
    public void testCreatePagerDutyIntegrationSerializesCorrectly() {
        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withBody("{\"id\":\"pd-id\",\"name\":\"PagerDuty\",\"type\":\"pagerduty\"}"))
        );

        Map<String, Object> endpoint = Map.of(
                "name", "PagerDuty Critical",
                "description", "PagerDuty for critical incidents",
                "type", "pagerduty",
                "enabled", true,
                "properties", Map.of("severity", "critical", "secret_token", "pd-key-123")
        );

        client.when()
                .toolsCall("createIntegration", Map.of("endpoint", endpoint), response -> {
                    assertFalse(response.isError());
                })
                .thenAssertResults();

        MockServerLifecycleManager.getClient().verify(
                postRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .withRequestBody(matchingJsonPath("$.type", equalTo("pagerduty")))
                        .withRequestBody(matchingJsonPath("$.properties.severity", equalTo("critical")))
                        .withRequestBody(matchingJsonPath("$.properties.secret_token", equalTo("pd-key-123")))
        );
    }

    // --- updateIntegration ---

    @Test
    public void testUpdateIntegration() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID))
                        .willReturn(aResponse().withStatus(200))
        );

        Map<String, Object> endpoint = Map.of(
                "name", "Updated Webhook", "description", "Updated webhook integration",
                "type", "webhook", "enabled", true,
                "properties", Map.of("url", "https://example.com/updated", "method", "POST", "disable_ssl_verification", false)
        );

        client.when()
                .toolsCall("updateIntegration",
                        Map.of("id", INTEGRATION_ID, "endpoint", endpoint), response -> {
                            assertFalse(response.isError());
                            assertTrue(response.firstContent().asText().text().contains("updated successfully"));
                        })
                .thenAssertResults();

        MockServerLifecycleManager.getClient().verify(
                putRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID))
        );
    }

    @Test
    public void testUpdateIntegrationBackendReturns400() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID))
                        .willReturn(aResponse().withStatus(400))
        );

        Map<String, Object> endpoint = Map.of(
                "name", "Updated", "description", "Updated webhook",
                "type", "webhook", "enabled", true,
                "properties", Map.of("url", "https://example.com", "method", "POST", "disable_ssl_verification", false)
        );

        client.when()
                .toolsCall("updateIntegration",
                        Map.of("id", INTEGRATION_ID, "endpoint", endpoint), response -> {
                            assertTrue(response.isError());
                            assertTrue(response.firstContent().asText().text().contains("Invalid request"));
                        })
                .thenAssertResults();
    }

    // --- addEventTypeToIntegration ---

    @Test
    public void testAddEventTypeToIntegration() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/eventType/" + EVENT_TYPE_ID))
                        .willReturn(aResponse().withStatus(204))
        );

        client.when()
                .toolsCall("addEventTypeToIntegration",
                        Map.of("endpointId", INTEGRATION_ID, "eventTypeId", EVENT_TYPE_ID), response -> {
                            assertFalse(response.isError());
                            assertTrue(response.firstContent().asText().text().contains("linked to integration successfully"));
                        })
                .thenAssertResults();

        MockServerLifecycleManager.getClient().verify(
                putRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/eventType/" + EVENT_TYPE_ID))
        );
    }

    @Test
    public void testAddEventTypeToIntegrationBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/eventType/" + EVENT_TYPE_ID))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("addEventTypeToIntegration",
                        Map.of("endpointId", INTEGRATION_ID, "eventTypeId", EVENT_TYPE_ID), response -> {
                            assertTrue(response.isError());
                            assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                        })
                .thenAssertResults();
    }

    // --- deleteEventTypeFromIntegration ---

    @Test
    public void testDeleteEventTypeFromIntegration() {
        MockServerLifecycleManager.getClient().stubFor(
                delete(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/eventType/" + EVENT_TYPE_ID))
                        .willReturn(aResponse().withStatus(204))
        );

        client.when()
                .toolsCall("deleteEventTypeFromIntegration",
                        Map.of("endpointId", INTEGRATION_ID, "eventTypeId", EVENT_TYPE_ID), response -> {
                            assertFalse(response.isError());
                            assertTrue(response.firstContent().asText().text().contains("unlinked from integration successfully"));
                        })
                .thenAssertResults();

        MockServerLifecycleManager.getClient().verify(
                deleteRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/eventType/" + EVENT_TYPE_ID))
        );
    }

    @Test
    public void testDeleteEventTypeFromIntegrationBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                delete(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/eventType/" + EVENT_TYPE_ID))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("deleteEventTypeFromIntegration",
                        Map.of("endpointId", INTEGRATION_ID, "eventTypeId", EVENT_TYPE_ID), response -> {
                            assertTrue(response.isError());
                            assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                        })
                .thenAssertResults();
    }

    // --- updateEventTypesLinkedToIntegration ---

    @Test
    public void testUpdateEventTypesLinkedToIntegration() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/eventTypes"))
                        .willReturn(aResponse().withStatus(204))
        );

        client.when()
                .toolsCall("updateEventTypesLinkedToIntegration",
                        Map.of("endpointId", INTEGRATION_ID,
                                "eventTypeIds", List.of(EVENT_TYPE_ID, "11111111-2222-3333-4444-555555555555")),
                        response -> {
                            assertFalse(response.isError());
                            assertTrue(response.firstContent().asText().text().contains("associations updated successfully"));
                        })
                .thenAssertResults();

        MockServerLifecycleManager.getClient().verify(
                putRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/eventTypes"))
        );
    }

    @Test
    public void testUpdateEventTypesLinkedToIntegrationBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/eventTypes"))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("updateEventTypesLinkedToIntegration",
                        Map.of("endpointId", INTEGRATION_ID,
                                "eventTypeIds", List.of(EVENT_TYPE_ID)),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                        })
                .thenAssertResults();
    }

    @Test
    public void testUpdateEventTypesLinkedToIntegrationBackendReturns403() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/" + INTEGRATION_ID + "/eventTypes"))
                        .willReturn(aResponse().withStatus(403))
        );

        client.when()
                .toolsCall("updateEventTypesLinkedToIntegration",
                        Map.of("endpointId", INTEGRATION_ID,
                                "eventTypeIds", List.of(EVENT_TYPE_ID)),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(response.firstContent().asText().text().contains("Access denied"));
                        })
                .thenAssertResults();
    }
}
