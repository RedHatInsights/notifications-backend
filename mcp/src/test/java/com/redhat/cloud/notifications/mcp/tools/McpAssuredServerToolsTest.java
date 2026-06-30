package com.redhat.cloud.notifications.mcp.tools;

import com.redhat.cloud.notifications.mcp.McpAssuredTestBase;
import com.redhat.cloud.notifications.mcp.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class McpAssuredServerToolsTest extends McpAssuredTestBase {

    @Test
    public void testListToolsContainsServerTools() {
        client.when()
                .toolsList(page -> {
                    assertNotNull(page.findByName("serverInfo"));
                    assertNotNull(page.findByName("whoami"));
                })
                .thenAssertResults();
    }

    @Test
    public void testServerInfo() {
        client.when()
                .toolsCall("serverInfo", Map.of(), response -> {
                    assertFalse(response.isError());
                    String text = response.firstContent().asText().text();
                    assertTrue(text.contains("Notifications MCP Server is running"));
                })
                .thenAssertResults();
    }

    @Test
    public void testWhoami() {
        client.when()
                .toolsCall("whoami", Map.of(), response -> {
                    assertFalse(response.isError());
                    String text = response.firstContent().asText().text();
                    assertTrue(text.contains(DEFAULT_ORG_ID));
                    assertTrue(text.contains(DEFAULT_USER));
                })
                .thenAssertResults();
    }
}
