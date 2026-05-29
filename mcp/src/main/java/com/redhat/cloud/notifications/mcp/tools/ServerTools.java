package com.redhat.cloud.notifications.mcp.tools;

import com.redhat.cloud.notifications.mcp.McpPrincipal;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ServerTools {

    @Inject
    SecurityIdentity securityIdentity;

    @Tool(description = "Returns the server status")
    public String serverInfo() {
        return "Notifications MCP Server is running.";
    }

    @Tool(description = "Returns information about the authenticated user")
    public String whoami() {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return String.format(
                "Authenticated as:%n" +
                "  Organization ID: %s%n" +
                "  User ID: %s%n" +
                "  Username: %s",
                principal.getOrgId(),
                principal.getUserId(),
                principal.getName()
        );
    }
}
