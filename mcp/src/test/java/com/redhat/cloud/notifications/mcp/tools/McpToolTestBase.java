package com.redhat.cloud.notifications.mcp.tools;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.mcp.McpTestHelpers;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static io.restassured.RestAssured.given;

/**
 * Base class for MCP tool tests providing shared setup and helper methods.
 * Subclasses must be annotated with @QuarkusTest and @QuarkusTestResource(TestLifecycleManager.class).
 */
public abstract class McpToolTestBase {

    protected static final String MCP_ENDPOINT = "/mcp";
    protected static final String ACCEPT_MCP = "application/json, text/event-stream";
    protected static final String AUTH_SUCCESS_COUNTER = "notifications.mcp.auth.success";
    protected static final String AUTH_FAILURE_COUNTER = "notifications.mcp.auth.failure";

    @Inject
    protected MicrometerAssertionHelper micrometerAssertionHelper;

    @BeforeEach
    void baseSetup() {
        MockServerLifecycleManager.getClient().resetAll();
        micrometerAssertionHelper.saveCounterValuesBeforeTest(AUTH_SUCCESS_COUNTER);
        micrometerAssertionHelper.saveCounterValueWithTagsBeforeTest(AUTH_FAILURE_COUNTER, "reason", "missing_header");
        micrometerAssertionHelper.saveCounterValueWithTagsBeforeTest(AUTH_FAILURE_COUNTER, "reason", "missing_org_id");
        micrometerAssertionHelper.saveCounterValueWithTagsBeforeTest(AUTH_FAILURE_COUNTER, "reason", "invalid_header");
        micrometerAssertionHelper.saveCounterValueWithTagsBeforeTest(AUTH_FAILURE_COUNTER, "reason", "missing_user_id");
        micrometerAssertionHelper.saveCounterValueWithTagsBeforeTest(AUTH_FAILURE_COUNTER, "reason", "missing_username");
    }

    @AfterEach
    void baseTeardown() {
        micrometerAssertionHelper.clearSavedValues();
    }

    protected static String validIdentity() {
        return McpTestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
    }

    protected ValidatableResponse postMcp(String identity, String body) {
        RequestSpecification request = given()
                .header("Accept", ACCEPT_MCP)
                .contentType(ContentType.JSON)
                .body(body);
        if (identity != null) {
            request = request.header("x-rh-identity", identity);
        }
        return request.when().post(MCP_ENDPOINT).then();
    }

    protected void assertAuthRejected(String identity, String body, String expectedReason) {
        postMcp(identity, body).statusCode(401);
        micrometerAssertionHelper.assertCounterIncrement(AUTH_FAILURE_COUNTER, 1, "reason", expectedReason);
    }
}
