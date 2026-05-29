package com.redhat.cloud.notifications.mcp;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
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

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests for MCP authentication mechanism and protocol initialization.
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class McpAuthenticationTest {

    private static final String MCP_ENDPOINT = "/mcp";
    private static final String ACCEPT_MCP = "application/json, text/event-stream";
    private static final String AUTH_SUCCESS_COUNTER = "notifications.mcp.auth.success";
    private static final String AUTH_FAILURE_COUNTER = "notifications.mcp.auth.failure";

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @BeforeEach
    void beforeEach() {
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
            request = request.header("x-rh-identity", identity);
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
