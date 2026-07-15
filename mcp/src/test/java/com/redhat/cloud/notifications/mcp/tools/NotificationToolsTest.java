package com.redhat.cloud.notifications.mcp.tools;

import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.mcp.McpTestBase;
import com.redhat.cloud.notifications.mcp.TestLifecycleManager;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

/**
 * Tests for NotificationTools: getSeverities, getBundle, getApplication, getEventType, getLinkedEndpoints, updateEventTypeEndpoints
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class NotificationToolsTest extends McpTestBase {

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

    private static final String GET_SEVERITIES_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 5,
                "params": {
                    "name": "getSeverities",
                    "arguments": {}
                }
            }
            """;

    private static final String GET_BUNDLE_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 6,
                "params": {
                    "name": "getBundle",
                    "arguments": {
                        "bundleName": "rhel"
                    }
                }
            }
            """;

    private static final String GET_APPLICATION_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 7,
                "params": {
                    "name": "getApplication",
                    "arguments": {
                        "bundleName": "rhel",
                        "applicationName": "patch"
                    }
                }
            }
            """;

    private static final String GET_EVENT_TYPE_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 8,
                "params": {
                    "name": "getEventType",
                    "arguments": {
                        "bundleName": "rhel",
                        "applicationName": "patch",
                        "eventTypeName": "new-advisory"
                    }
                }
            }
            """;

    // --- getSeverities tests ---

    @Test
    public void testGetSeveritiesWithValidIdentity() {
        String severitiesJson = "[\"CRITICAL\",\"IMPORTANT\",\"MODERATE\",\"LOW\",\"NONE\",\"UNDEFINED\"]";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v2.0/notifications/severities"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(severitiesJson))
        );

        postMcp(validIdentity(), GET_SEVERITIES_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("CRITICAL"))
                .body("result.content[0].text", containsString("IMPORTANT"))
                .body("result.content[0].text", containsString("MODERATE"))
                .body("result.content[0].text", containsString("LOW"))
                .body("result.content[0].text", containsString("NONE"))
                .body("result.content[0].text", containsString("UNDEFINED"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/notifications/v2.0/notifications/severities"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetSeveritiesWithoutIdentityIsRejected() {
        assertAuthRejected(null, GET_SEVERITIES_BODY, "missing_header");
    }

    @Test
    public void testGetSeveritiesWhenBackendReturns500() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v2.0/notifications/severities"))
                        .willReturn(aResponse().withStatus(500))
        );

        postMcp(validIdentity(), GET_SEVERITIES_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Backend service error, try again later"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testGetSeveritiesWhenBackendReturns403() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v2.0/notifications/severities"))
                        .willReturn(aResponse().withStatus(403))
        );

        postMcp(validIdentity(), GET_SEVERITIES_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Access denied"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testGetSeveritiesWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v2.0/notifications/severities"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_SEVERITIES_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- getBundle tests ---

    @Test
    public void testGetBundleWithValidIdentity() {
        String bundleJson = "{\"id\":\"1234\",\"name\":\"rhel\",\"display_name\":\"Red Hat Enterprise Linux\"}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/bundles/rhel"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(bundleJson))
        );

        postMcp(validIdentity(), GET_BUNDLE_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("rhel"))
                .body("result.content[0].text", containsString("Red Hat Enterprise Linux"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/notifications/v1.0/notifications/bundles/rhel"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetBundleWithoutIdentityIsRejected() {
        assertAuthRejected(null, GET_BUNDLE_BODY, "missing_header");
    }

    @Test
    public void testGetBundleWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/bundles/rhel"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_BUNDLE_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- getApplication tests ---

    @Test
    public void testGetApplicationWithValidIdentity() {
        String applicationJson = "{\"id\":\"5678\",\"name\":\"patch\",\"display_name\":\"Patch\",\"bundle_id\":\"1234\"}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/bundles/rhel/applications/patch"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(applicationJson))
        );

        postMcp(validIdentity(), GET_APPLICATION_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("patch"))
                .body("result.content[0].text", containsString("Patch"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/notifications/v1.0/notifications/bundles/rhel/applications/patch"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetApplicationWithoutIdentityIsRejected() {
        assertAuthRejected(null, GET_APPLICATION_BODY, "missing_header");
    }

    @Test
    public void testGetApplicationWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/bundles/rhel/applications/patch"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_APPLICATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- getEventType tests ---

    @Test
    public void testGetEventTypeWithValidIdentity() {
        String eventTypeJson = "{\"id\":\"9012\",\"name\":\"new-advisory\",\"display_name\":\"New advisory\",\"application_id\":\"5678\"}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/bundles/rhel/applications/patch/eventTypes/new-advisory"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(eventTypeJson))
        );

        postMcp(validIdentity(), GET_EVENT_TYPE_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("new-advisory"))
                .body("result.content[0].text", containsString("New advisory"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/notifications/v1.0/notifications/bundles/rhel/applications/patch/eventTypes/new-advisory"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetEventTypeWithoutIdentityIsRejected() {
        assertAuthRejected(null, GET_EVENT_TYPE_BODY, "missing_header");
    }

    @Test
    public void testGetEventTypeWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/bundles/rhel/applications/patch/eventTypes/new-advisory"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_EVENT_TYPE_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- getLinkedEndpoints tests ---

    private static final String GET_LINKED_ENDPOINTS_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 20,
                "params": {
                    "name": "getLinkedIntegrations",
                    "arguments": {
                        "eventTypeId": "550e8400-e29b-41d4-a716-446655440000"
                    }
                }
            }
            """;

    @Test
    public void testGetLinkedEndpointsWithValidIdentity() {
        String endpointsJson = """
                {
                    "data": [
                        {"id": "endpoint-1", "name": "Webhook 1", "type": "webhook"},
                        {"id": "endpoint-2", "name": "Slack Integration", "type": "camel"}
                    ],
                    "meta": {"count": 2}
                }
                """;
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/eventTypes/550e8400-e29b-41d4-a716-446655440000/endpoints"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(endpointsJson))
        );

        postMcp(validIdentity(), GET_LINKED_ENDPOINTS_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("endpoint-1"))
                .body("result.content[0].text", containsString("Webhook 1"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/notifications/v1.0/notifications/eventTypes/550e8400-e29b-41d4-a716-446655440000/endpoints"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetLinkedEndpointsWithoutIdentityIsRejected() {
        assertAuthRejected(null, GET_LINKED_ENDPOINTS_BODY, "missing_header");
    }

    @Test
    public void testGetLinkedEndpointsWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/eventTypes/550e8400-e29b-41d4-a716-446655440000/endpoints"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_LINKED_ENDPOINTS_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testGetLinkedEndpointsWhenBackendReturns403() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/eventTypes/550e8400-e29b-41d4-a716-446655440000/endpoints"))
                        .willReturn(aResponse().withStatus(403))
        );

        postMcp(validIdentity(), GET_LINKED_ENDPOINTS_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Access denied"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- updateEventTypeEndpoints tests ---

    private static final String UPDATE_EVENT_TYPE_ENDPOINTS_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 25,
                "params": {
                    "name": "updateEventTypeIntegrations",
                    "arguments": {
                        "eventTypeId": "550e8400-e29b-41d4-a716-446655440000",
                        "endpointIds": ["660e8400-e29b-41d4-a716-446655440001", "660e8400-e29b-41d4-a716-446655440002"]
                    }
                }
            }
            """;

    private static final String UPDATE_EVENT_TYPE_ENDPOINTS_EMPTY_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 26,
                "params": {
                    "name": "updateEventTypeIntegrations",
                    "arguments": {
                        "eventTypeId": "550e8400-e29b-41d4-a716-446655440000",
                        "endpointIds": []
                    }
                }
            }
            """;

    @Test
    public void testUpdateEventTypeEndpointsWithValidIdentity() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/notifications/v1.0/notifications/eventTypes/550e8400-e29b-41d4-a716-446655440000/endpoints"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse().withStatus(200))
        );

        postMcp(validIdentity(), UPDATE_EVENT_TYPE_ENDPOINTS_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("updated successfully"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                putRequestedFor(urlPathEqualTo("/api/notifications/v1.0/notifications/eventTypes/550e8400-e29b-41d4-a716-446655440000/endpoints"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testUpdateEventTypeEndpointsWithEmptySet() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/notifications/v1.0/notifications/eventTypes/550e8400-e29b-41d4-a716-446655440000/endpoints"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse().withStatus(200))
        );

        postMcp(validIdentity(), UPDATE_EVENT_TYPE_ENDPOINTS_EMPTY_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("updated successfully"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testUpdateEventTypeEndpointsWithoutIdentityIsRejected() {
        assertAuthRejected(null, UPDATE_EVENT_TYPE_ENDPOINTS_BODY, "missing_header");
    }

    @Test
    public void testUpdateEventTypeEndpointsWhenBackendReturns400() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/notifications/v1.0/notifications/eventTypes/550e8400-e29b-41d4-a716-446655440000/endpoints"))
                        .willReturn(aResponse().withStatus(400))
        );

        postMcp(validIdentity(), UPDATE_EVENT_TYPE_ENDPOINTS_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Invalid request"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testUpdateEventTypeEndpointsWhenBackendReturns403() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/notifications/v1.0/notifications/eventTypes/550e8400-e29b-41d4-a716-446655440000/endpoints"))
                        .willReturn(aResponse().withStatus(403))
        );

        postMcp(validIdentity(), UPDATE_EVENT_TYPE_ENDPOINTS_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Access denied"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testUpdateEventTypeEndpointsWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/notifications/v1.0/notifications/eventTypes/550e8400-e29b-41d4-a716-446655440000/endpoints"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), UPDATE_EVENT_TYPE_ENDPOINTS_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }
}
