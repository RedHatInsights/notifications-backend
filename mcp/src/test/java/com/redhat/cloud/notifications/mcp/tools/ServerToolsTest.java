package com.redhat.cloud.notifications.mcp.tools;

import com.redhat.cloud.notifications.mcp.McpTestHelpers;
import com.redhat.cloud.notifications.mcp.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static org.hamcrest.Matchers.containsString;

/**
 * Tests for ServerTools: serverInfo, whoami
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ServerToolsTest extends McpToolTestBase {

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
}
