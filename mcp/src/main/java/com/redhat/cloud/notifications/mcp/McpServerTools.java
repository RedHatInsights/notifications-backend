package com.redhat.cloud.notifications.mcp;

import io.quarkiverse.mcp.server.Tool;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Placeholder MCP tools for the Notifications MCP server.
 */
@ApplicationScoped
public class McpServerTools {

    @Tool(description = "Returns the server status and version information")
    public String serverInfo() {
        return "Notifications MCP Server is running.";
    }
}
