package com.redhat.cloud.notifications.mcp.tools;

import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.mcp.McpAssuredTestBase;
import com.redhat.cloud.notifications.mcp.TestLifecycleManager;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class McpAssuredNotificationToolsTest extends McpAssuredTestBase {

    @CacheName("mcp-get-severities")
    Cache severitiesCache;

    @CacheName("mcp-get-bundle")
    Cache bundleCache;

    @CacheName("mcp-get-application")
    Cache applicationCache;

    @CacheName("mcp-get-event-type")
    Cache eventTypeCache;

    @BeforeEach
    void clearCaches() {
        severitiesCache.invalidateAll().await().indefinitely();
        bundleCache.invalidateAll().await().indefinitely();
        applicationCache.invalidateAll().await().indefinitely();
        eventTypeCache.invalidateAll().await().indefinitely();
    }

    @Test
    public void testListToolsContainsNotificationTools() {
        client.when()
                .toolsList(page -> {
                    assertNotNull(page.findByName("getSeverities"));
                    assertNotNull(page.findByName("getBundle"));
                    assertNotNull(page.findByName("getApplication"));
                    assertNotNull(page.findByName("getEventType"));
                    assertNotNull(page.findByName("getLinkedIntegrations"));
                    assertNotNull(page.findByName("updateEventTypeIntegrations"));
                })
                .thenAssertResults();
    }

    // --- getSeverities ---

    @Test
    public void testGetSeverities() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v2.0/notifications/severities"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("[\"CRITICAL\",\"IMPORTANT\",\"MODERATE\",\"LOW\",\"NONE\",\"UNDEFINED\"]"))
        );

        client.when()
                .toolsCall("getSeverities", Map.of(), response -> {
                    assertFalse(response.isError());
                    String text = response.firstContent().asText().text();
                    assertTrue(text.contains("CRITICAL"));
                    assertTrue(text.contains("IMPORTANT"));
                    assertTrue(text.contains("MODERATE"));
                })
                .thenAssertResults();
    }

    @Test
    public void testGetSeveritiesBackendReturns500() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v2.0/notifications/severities"))
                        .willReturn(aResponse().withStatus(500))
        );

        client.when()
                .toolsCall("getSeverities", Map.of(), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Backend service error"));
                })
                .thenAssertResults();
    }

    @Test
    public void testGetSeveritiesBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v2.0/notifications/severities"))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("getSeverities", Map.of(), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                })
                .thenAssertResults();
    }

    // --- getBundle ---

    @Test
    public void testGetBundle() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/bundles/rhel"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"id\":\"1234\",\"name\":\"rhel\",\"display_name\":\"Red Hat Enterprise Linux\"}"))
        );

        client.when()
                .toolsCall("getBundle", Map.of("bundleName", "rhel"), response -> {
                    assertFalse(response.isError());
                    String text = response.firstContent().asText().text();
                    assertTrue(text.contains("rhel"));
                    assertTrue(text.contains("Red Hat Enterprise Linux"));
                })
                .thenAssertResults();
    }

    @Test
    public void testGetBundleBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/bundles/rhel"))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("getBundle", Map.of("bundleName", "rhel"), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                })
                .thenAssertResults();
    }

    // --- getApplication ---

    @Test
    public void testGetApplication() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/bundles/rhel/applications/patch"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"id\":\"5678\",\"name\":\"patch\",\"display_name\":\"Patch\",\"bundle_id\":\"1234\"}"))
        );

        client.when()
                .toolsCall("getApplication", Map.of("bundleName", "rhel", "applicationName", "patch"), response -> {
                    assertFalse(response.isError());
                    String text = response.firstContent().asText().text();
                    assertTrue(text.contains("patch"));
                    assertTrue(text.contains("Patch"));
                })
                .thenAssertResults();
    }

    @Test
    public void testGetApplicationBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/bundles/rhel/applications/patch"))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("getApplication", Map.of("bundleName", "rhel", "applicationName", "patch"), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                })
                .thenAssertResults();
    }

    // --- getEventType ---

    @Test
    public void testGetEventType() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/bundles/rhel/applications/patch/eventTypes/new-advisory"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"id\":\"9012\",\"name\":\"new-advisory\",\"display_name\":\"New advisory\",\"application_id\":\"5678\"}"))
        );

        client.when()
                .toolsCall("getEventType",
                        Map.of("bundleName", "rhel", "applicationName", "patch", "eventTypeName", "new-advisory"),
                        response -> {
                            assertFalse(response.isError());
                            String text = response.firstContent().asText().text();
                            assertTrue(text.contains("new-advisory"));
                            assertTrue(text.contains("New advisory"));
                        })
                .thenAssertResults();
    }

    @Test
    public void testGetEventTypeBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/bundles/rhel/applications/patch/eventTypes/new-advisory"))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("getEventType",
                        Map.of("bundleName", "rhel", "applicationName", "patch", "eventTypeName", "new-advisory"),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                        })
                .thenAssertResults();
    }

    // --- getLinkedIntegrations ---

    @Test
    public void testGetLinkedIntegrations() {
        String endpointsJson = """
                {"data":[{"id":"endpoint-1","name":"Webhook 1","type":"webhook"},{"id":"endpoint-2","name":"Slack Integration","type":"camel"}],"meta":{"count":2}}
                """;
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/eventTypes/550e8400-e29b-41d4-a716-446655440000/endpoints"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(endpointsJson))
        );

        client.when()
                .toolsCall("getLinkedIntegrations",
                        Map.of("eventTypeId", "550e8400-e29b-41d4-a716-446655440000"), response -> {
                            assertFalse(response.isError());
                            String text = response.firstContent().asText().text();
                            assertTrue(text.contains("endpoint-1"));
                            assertTrue(text.contains("Webhook 1"));
                        })
                .thenAssertResults();
    }

    @Test
    public void testGetLinkedIntegrationsBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/eventTypes/550e8400-e29b-41d4-a716-446655440000/endpoints"))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("getLinkedIntegrations",
                        Map.of("eventTypeId", "550e8400-e29b-41d4-a716-446655440000"), response -> {
                            assertTrue(response.isError());
                            assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                        })
                .thenAssertResults();
    }

    // --- updateEventTypeIntegrations ---

    @Test
    public void testUpdateEventTypeIntegrations() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/notifications/v1.0/notifications/eventTypes/550e8400-e29b-41d4-a716-446655440000/endpoints"))
                        .willReturn(aResponse().withStatus(200))
        );

        client.when()
                .toolsCall("updateEventTypeIntegrations",
                        Map.of("eventTypeId", "550e8400-e29b-41d4-a716-446655440000",
                                "endpointIds", List.of("660e8400-e29b-41d4-a716-446655440001")),
                        response -> {
                            assertFalse(response.isError());
                            assertTrue(response.firstContent().asText().text().contains("updated successfully"));
                        })
                .thenAssertResults();

        MockServerLifecycleManager.getClient().verify(
                putRequestedFor(urlPathEqualTo("/api/notifications/v1.0/notifications/eventTypes/550e8400-e29b-41d4-a716-446655440000/endpoints"))
        );
    }

    @Test
    public void testUpdateEventTypeIntegrationsBackendReturns403() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/notifications/v1.0/notifications/eventTypes/550e8400-e29b-41d4-a716-446655440000/endpoints"))
                        .willReturn(aResponse().withStatus(403))
        );

        client.when()
                .toolsCall("updateEventTypeIntegrations",
                        Map.of("eventTypeId", "550e8400-e29b-41d4-a716-446655440000",
                                "endpointIds", List.of("660e8400-e29b-41d4-a716-446655440001")),
                        response -> {
                            assertTrue(response.isError());
                            assertTrue(response.firstContent().asText().text().contains("Access denied"));
                        })
                .thenAssertResults();
    }
}
