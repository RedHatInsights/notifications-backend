package com.redhat.cloud.notifications.mcp;

import com.redhat.cloud.notifications.MockServerLifecycleManager;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;

/**
 * Base class for McpAssured-based tool tests.
 * Uses the Quarkus MCP Server testing library instead of raw JSON-RPC payloads.
 */
public abstract class McpAssuredTestBase {

    protected McpStreamableTestClient client;

    @BeforeEach
    void setUp() {
        MockServerLifecycleManager.getClient().resetAll();
        String identity = McpTestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        client = McpAssured.newStreamableClient()
                .setAdditionalHeaders(msg -> {
                    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
                    headers.add("x-rh-identity", identity);
                    return headers;
                })
                .build()
                .connect();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.disconnect();
        }
    }
}
