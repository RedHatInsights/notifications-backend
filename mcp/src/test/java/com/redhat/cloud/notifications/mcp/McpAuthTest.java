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
                .body("result.tools.size()", greaterThanOrEqualTo(6))
                .body("result.tools.name", hasItems("serverInfo", "whoami", "getSeverities",
                        "getBundle", "getApplication", "getEventType"));
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
