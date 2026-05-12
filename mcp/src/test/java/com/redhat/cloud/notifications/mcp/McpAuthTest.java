package com.redhat.cloud.notifications.mcp;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static com.redhat.cloud.notifications.mcp.McpAuthMechanism.X_RH_IDENTITY_HEADER;
import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests for MCP x-rh-identity authentication.
 * Note: The MCP Streamable HTTP transport requires Accept: text/event-stream.
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class McpAuthTest {

    private static final String MCP_ENDPOINT = "/mcp";
    private static final String ACCEPT_MCP = "application/json, text/event-stream";

    private static final String AUTH_SUCCESS_COUNTER = "notifications.mcp.auth.success";
    private static final String AUTH_FAILURE_COUNTER = "notifications.mcp.auth.failure";

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @CacheName("mcp-get-severities")
    Cache severitiesCache;

    @CacheName("mcp-get-bundle")
    Cache bundleCache;

    @CacheName("mcp-get-application")
    Cache applicationCache;

    @CacheName("mcp-get-event-type")
    Cache eventTypeCache;

    @BeforeEach
    void beforeEach() {
        MockServerLifecycleManager.getClient().resetAll();
        severitiesCache.invalidateAll().await().indefinitely();
        bundleCache.invalidateAll().await().indefinitely();
        applicationCache.invalidateAll().await().indefinitely();
        eventTypeCache.invalidateAll().await().indefinitely();
        micrometerAssertionHelper.saveCounterValuesBeforeTest(AUTH_SUCCESS_COUNTER);
        micrometerAssertionHelper.saveCounterValueWithTagsBeforeTest(AUTH_FAILURE_COUNTER, "reason", "missing_header");
        micrometerAssertionHelper.saveCounterValueWithTagsBeforeTest(AUTH_FAILURE_COUNTER, "reason", "missing_org_id");
        micrometerAssertionHelper.saveCounterValueWithTagsBeforeTest(AUTH_FAILURE_COUNTER, "reason", "invalid_header");
        micrometerAssertionHelper.saveCounterValueWithTagsBeforeTest(AUTH_FAILURE_COUNTER, "reason", "missing_user_id");
        micrometerAssertionHelper.saveCounterValueWithTagsBeforeTest(AUTH_FAILURE_COUNTER, "reason", "missing_username");
    }

    @AfterEach
    void afterEach() {
        micrometerAssertionHelper.clearSavedValues();
    }

    private static final String INITIALIZE_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "initialize",
                "id": 1,
                "params": {
                    "protocolVersion": "2025-03-26",
                    "capabilities": {},
                    "clientInfo": {
                        "name": "test-client",
                        "version": "1.0.0"
                    },
                    "implementation": {
                        "name": "test-client",
                        "version": "1.0.0"
                    }
                }
            }
            """;

    private static final String TOOLS_LIST_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/list",
                "id": 4,
                "params": {}
            }
            """;

    private static final String SERVER_INFO_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 2,
                "params": {
                    "name": "serverInfo",
                    "arguments": {}
                }
            }
            """;

    private static final String WHOAMI_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 3,
                "params": {
                    "name": "whoami",
                    "arguments": {}
                }
            }
            """;

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

    private static final String GET_INTEGRATIONS_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 9,
                "params": {
                    "name": "getIntegrations",
                    "arguments": {}
                }
            }
            """;

    private static final String GET_INTEGRATION_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 10,
                "params": {
                    "name": "getIntegration",
                    "arguments": {
                        "id": "12345678-abcd-1234-abcd-1234567890ab"
                    }
                }
            }
            """;

    private static final String GET_INTEGRATION_HISTORY_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 11,
                "params": {
                    "name": "getIntegrationHistory",
                    "arguments": {
                        "id": "12345678-abcd-1234-abcd-1234567890ab"
                    }
                }
            }
            """;

    private static final String GET_INTEGRATION_HISTORY_DETAILS_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 12,
                "params": {
                    "name": "getIntegrationHistoryDetails",
                    "arguments": {
                        "integrationId": "12345678-abcd-1234-abcd-1234567890ab",
                        "historyId": "abcd1234-abcd-1234-abcd-1234567890ab"
                    }
                }
            }
            """;

    private static final String GET_EVENTS_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 13,
                "params": {
                    "name": "getEvents",
                    "arguments": {}
                }
            }
            """;

    private static final String GET_DAILY_DIGEST_TIME_PREFERENCE_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 14,
                "params": {
                    "name": "getDailyDigestTimePreference",
                    "arguments": {}
                }
            }
            """;

    private static final String GET_USER_NOTIFICATION_PREFERENCES_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 15,
                "params": {
                    "name": "getUserNotificationPreferences",
                    "arguments": {}
                }
            }
            """;

    private static final String GET_USER_NOTIFICATION_PREFERENCES_BY_APP_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 16,
                "params": {
                    "name": "getUserNotificationPreferencesByApplication",
                    "arguments": {
                        "bundleName": "rhel",
                        "applicationName": "patch"
                    }
                }
            }
            """;

    // --- Helpers ---

    private static String validIdentity() {
        return McpTestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
    }

    private static String base64Encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(UTF_8));
    }

    private ValidatableResponse postMcp(String identity, String body) {
        RequestSpecification request = given()
                .header("Accept", ACCEPT_MCP)
                .contentType(ContentType.JSON)
                .body(body);
        if (identity != null) {
            request = request.header(X_RH_IDENTITY_HEADER, identity);
        }
        return request.when().post(MCP_ENDPOINT).then();
    }

    private void assertInitializeRejected(String identity, String expectedReason) {
        postMcp(identity, INITIALIZE_BODY).statusCode(401);
        micrometerAssertionHelper.assertCounterIncrement(AUTH_FAILURE_COUNTER, 1, "reason", expectedReason);
    }

    // --- Initialize tests ---

    @Test
    public void testMcpInitializeWithValidIdentity() {
        postMcp(validIdentity(), INITIALIZE_BODY)
                .statusCode(200)
                .body("result.protocolVersion", notNullValue())
                .body("result.serverInfo.name", notNullValue());
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testMcpInitializeWithoutIdentity() {
        assertInitializeRejected(null, "missing_header");
    }

    @Test
    public void testMcpInitializeWithInvalidBase64() {
        assertInitializeRejected("!!!invalid-base64!!!", "invalid_header");
    }

    @Test
    public void testMcpInitializeWithValidBase64InvalidJson() {
        assertInitializeRejected(base64Encode("{not-valid-json"), "invalid_header");
    }

    @Test
    public void testMcpInitializeWithMissingIdentityField() {
        assertInitializeRejected(base64Encode("{\"wrong\": \"field\"}"), "invalid_header");
    }

    @Test
    public void testMcpInitializeWithServiceAccountIdentityIsRejected() {
        assertInitializeRejected(
                McpTestHelpers.encodeRHServiceAccountIdentityInfo(DEFAULT_ORG_ID, "sa-client-id", "service-account-name"),
                "invalid_header"
        );
    }

    @Test
    public void testMcpInitializeWithoutOrgId() {
        assertInitializeRejected(
                McpTestHelpers.encodeRHIdentityInfoWithoutOrgId(DEFAULT_ACCOUNT_ID, DEFAULT_USER),
                "missing_org_id"
        );
    }

    @Test
    public void testMcpInitializeWithEmptyOrgId() {
        assertInitializeRejected(
                McpTestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, "   ", DEFAULT_USER),
                "missing_org_id"
        );
    }

    @Test
    public void testMcpInitializeWithoutUserIdIsRejected() {
        assertInitializeRejected(
                McpTestHelpers.encodeRHIdentityInfoWithoutUserId(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER),
                "missing_user_id"
        );
    }

    @Test
    public void testMcpInitializeWithEmptyUserIdIsRejected() {
        assertInitializeRejected(
                McpTestHelpers.encodeRHIdentityInfoWithCustomUserId(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, "   "),
                "missing_user_id"
        );
    }

    @Test
    public void testMcpInitializeWithoutUsernameIsRejected() {
        assertInitializeRejected(
                McpTestHelpers.encodeRHIdentityInfoWithoutUsername(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER),
                "missing_username"
        );
    }

    @Test
    public void testMcpInitializeWithEmptyUsernameIsRejected() {
        assertInitializeRejected(
                McpTestHelpers.encodeRHIdentityInfoWithCustomUsername(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, "   "),
                "missing_username"
        );
    }

    // --- Tools list tests ---

    @Test
    public void testToolsListWithValidIdentity() {
        postMcp(validIdentity(), TOOLS_LIST_BODY)
                .statusCode(200)
                .body("result.tools.size()", greaterThanOrEqualTo(14))
                .body("result.tools.name", hasItems("serverInfo", "whoami", "getSeverities",
                        "getBundle", "getApplication", "getEventType",
                        "getIntegrations", "getIntegration", "getIntegrationHistory", "getIntegrationHistoryDetails",
                        "getEvents", "getDailyDigestTimePreference",
                        "getUserNotificationPreferences", "getUserNotificationPreferencesByApplication"));
    }

    @Test
    public void testToolsListWithoutIdentity() {
        postMcp(null, TOOLS_LIST_BODY).statusCode(401);
    }

    // --- Tool invocation tests ---

    @Test
    public void testServerInfoWithAuthentication() {
        postMcp(validIdentity(), SERVER_INFO_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("Notifications MCP Server is running"));
    }

    @Test
    public void testAuthenticatedToolWhoami() {
        postMcp(validIdentity(), WHOAMI_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString(DEFAULT_ORG_ID))
                .body("result.content[0].text", containsString(DEFAULT_USER));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testWhoamiWithServiceAccountIdentityIsRejected() {
        String identity = McpTestHelpers.encodeRHServiceAccountIdentityInfo(DEFAULT_ORG_ID, "sa-client-id", "sa-name");
        postMcp(identity, WHOAMI_BODY).statusCode(401);
        micrometerAssertionHelper.assertCounterIncrement(AUTH_FAILURE_COUNTER, 1, "reason", "invalid_header");
    }

    // --- getSeverities tool tests ---

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
        postMcp(null, GET_SEVERITIES_BODY).statusCode(401);
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

    // --- getBundle tool tests ---

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
        postMcp(null, GET_BUNDLE_BODY).statusCode(401);
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

    // --- getApplication tool tests ---

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
        postMcp(null, GET_APPLICATION_BODY).statusCode(401);
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

    // --- getEventType tool tests ---

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
        postMcp(null, GET_EVENT_TYPE_BODY).statusCode(401);
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

    // --- getIntegrations tool tests ---

    @Test
    public void testGetIntegrationsWithValidIdentity() {
        String integrationsJson = "{\"data\":[{\"id\":\"12345678-abcd-1234-abcd-1234567890ab\",\"name\":\"My Webhook\",\"type\":\"webhook\"}],\"meta\":{\"count\":1},\"links\":{}}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v2.0/endpoints"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(integrationsJson))
        );

        postMcp(validIdentity(), GET_INTEGRATIONS_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("My Webhook"))
                .body("result.content[0].text", containsString("webhook"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/integrations/v2.0/endpoints"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetIntegrationsWithoutIdentityIsRejected() {
        postMcp(null, GET_INTEGRATIONS_BODY).statusCode(401);
    }

    @Test
    public void testGetIntegrationsWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v2.0/endpoints"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_INTEGRATIONS_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- getIntegration tool tests ---

    @Test
    public void testGetIntegrationWithValidIdentity() {
        String integrationJson = "{\"id\":\"12345678-abcd-1234-abcd-1234567890ab\",\"name\":\"My Webhook\",\"type\":\"webhook\",\"enabled\":true}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v2.0/endpoints/12345678-abcd-1234-abcd-1234567890ab"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(integrationJson))
        );

        postMcp(validIdentity(), GET_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("My Webhook"))
                .body("result.content[0].text", containsString("12345678-abcd-1234-abcd-1234567890ab"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/integrations/v2.0/endpoints/12345678-abcd-1234-abcd-1234567890ab"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetIntegrationWithoutIdentityIsRejected() {
        postMcp(null, GET_INTEGRATION_BODY).statusCode(401);
    }

    @Test
    public void testGetIntegrationWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v2.0/endpoints/12345678-abcd-1234-abcd-1234567890ab"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- getIntegrationHistory tool tests ---

    @Test
    public void testGetIntegrationHistoryWithValidIdentity() {
        String historyJson = "{\"data\":[{\"id\":\"abcd1234-abcd-1234-abcd-1234567890ab\",\"status\":\"SUCCESS\"}],\"meta\":{\"count\":1},\"links\":{}}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v2.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/history"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(historyJson))
        );

        postMcp(validIdentity(), GET_INTEGRATION_HISTORY_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("SUCCESS"))
                .body("result.content[0].text", containsString("abcd1234"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/integrations/v2.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/history"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetIntegrationHistoryWithoutIdentityIsRejected() {
        postMcp(null, GET_INTEGRATION_HISTORY_BODY).statusCode(401);
    }

    @Test
    public void testGetIntegrationHistoryWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v2.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/history"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_INTEGRATION_HISTORY_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- getIntegrationHistoryDetails tool tests ---

    @Test
    public void testGetIntegrationHistoryDetailsWithValidIdentity() {
        String detailsJson = "{\"type\":\"com.redhat.console.notification.toCamel.webhook\",\"target\":\"https://example.com/hook\",\"outcome\":\"SUCCESS\"}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/history/abcd1234-abcd-1234-abcd-1234567890ab/details"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(detailsJson))
        );

        postMcp(validIdentity(), GET_INTEGRATION_HISTORY_DETAILS_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("webhook"))
                .body("result.content[0].text", containsString("SUCCESS"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/history/abcd1234-abcd-1234-abcd-1234567890ab/details"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetIntegrationHistoryDetailsWithoutIdentityIsRejected() {
        postMcp(null, GET_INTEGRATION_HISTORY_DETAILS_BODY).statusCode(401);
    }

    @Test
    public void testGetIntegrationHistoryDetailsWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/history/abcd1234-abcd-1234-abcd-1234567890ab/details"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_INTEGRATION_HISTORY_DETAILS_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- getEvents tool tests ---

    @Test
    public void testGetEventsWithValidIdentity() {
        String eventsJson = "{\"data\":[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"bundle\":\"rhel\",\"application\":\"patch\",\"event_type\":\"New advisory\",\"created\":\"2026-05-12T10:00:00\"}],\"meta\":{\"count\":1},\"links\":{}}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/events"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(eventsJson))
        );

        postMcp(validIdentity(), GET_EVENTS_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("rhel"))
                .body("result.content[0].text", containsString("patch"))
                .body("result.content[0].text", containsString("New advisory"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/notifications/v1.0/notifications/events"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetEventsWithoutIdentityIsRejected() {
        postMcp(null, GET_EVENTS_BODY).statusCode(401);
    }

    @Test
    public void testGetEventsWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/events"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_EVENTS_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- getDailyDigestTimePreference tool tests ---

    @Test
    public void testGetDailyDigestTimePreferenceWithValidIdentity() {
        String timeJson = "\"09:00\"";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/org-config/daily-digest/time-preference"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(timeJson))
        );

        postMcp(validIdentity(), GET_DAILY_DIGEST_TIME_PREFERENCE_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("09:00"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/notifications/v1.0/org-config/daily-digest/time-preference"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetDailyDigestTimePreferenceWithoutIdentityIsRejected() {
        postMcp(null, GET_DAILY_DIGEST_TIME_PREFERENCE_BODY).statusCode(401);
    }

    @Test
    public void testGetDailyDigestTimePreferenceWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/org-config/daily-digest/time-preference"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_DAILY_DIGEST_TIME_PREFERENCE_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- getUserNotificationPreferences tool tests ---

    @Test
    public void testGetUserNotificationPreferencesWithValidIdentity() {
        String prefsJson = "{\"bundles\":{\"rhel\":{\"display_name\":\"Red Hat Enterprise Linux\",\"applications\":{}}}}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(prefsJson))
        );

        postMcp(validIdentity(), GET_USER_NOTIFICATION_PREFERENCES_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("rhel"))
                .body("result.content[0].text", containsString("Red Hat Enterprise Linux"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetUserNotificationPreferencesWithoutIdentityIsRejected() {
        postMcp(null, GET_USER_NOTIFICATION_PREFERENCES_BODY).statusCode(401);
    }

    @Test
    public void testGetUserNotificationPreferencesWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_USER_NOTIFICATION_PREFERENCES_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- getUserNotificationPreferencesByApplication tool tests ---

    @Test
    public void testGetUserNotificationPreferencesByApplicationWithValidIdentity() {
        String appPrefsJson = "{\"display_name\":\"Patch\",\"event_types\":{\"new-advisory\":{\"display_name\":\"New advisory\"}}}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference/rhel/patch"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(appPrefsJson))
        );

        postMcp(validIdentity(), GET_USER_NOTIFICATION_PREFERENCES_BY_APP_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("Patch"))
                .body("result.content[0].text", containsString("New advisory"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference/rhel/patch"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetUserNotificationPreferencesByApplicationWithoutIdentityIsRejected() {
        postMcp(null, GET_USER_NOTIFICATION_PREFERENCES_BY_APP_BODY).statusCode(401);
    }

    @Test
    public void testGetUserNotificationPreferencesByApplicationWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference/rhel/patch"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_USER_NOTIFICATION_PREFERENCES_BY_APP_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- Health/metrics bypass tests ---

    @Test
    public void testHealthEndpointDoesNotRequireAuth() {
        given()
                .when()
                .get("/q/health/live")
                .then()
                .statusCode(200);
    }
}
