package com.redhat.cloud.notifications.mcp;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.function.Supplier;

/**
 * MCP tools for the Notifications MCP server.
 */
@ApplicationScoped
public class McpServerTools {

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    @RestClient
    BackendRestClient backendClient;

    @Inject
    MeterRegistry registry;

    @Tool(description = "Returns the server status and version information")
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

    @Tool(description = "Returns the list of available notification severities")
    public String getSeverities() {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return executeRestCall("getSeverities", principal,
                () -> backendClient.getSeverities(principal.getRawHeader()));
    }

    /**
     * Executes a REST client call, translating REST exceptions into MCP ToolCallExceptions.
     * Unexpected exceptions are left unhandled so the framework's default handler logs and
     * returns INTERNAL_ERROR automatically.
     */
    private <T> T executeRestCall(String toolName, McpPrincipal principal, Supplier<T> restCall) {
        try {
            return restCall.get();
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            Log.warnf(e, "Tool '%s' failed (HTTP %d) for org %s, user %s",
                    toolName, status, principal.getOrgId(), principal.getUserId());
            registry.counter("notifications.mcp.tool.error", "tool", toolName, "type", "http_" + status).increment();
            throw new ToolCallException(httpErrorMessage(status));
        } catch (ProcessingException e) {
            Log.errorf(e, "Tool '%s' connection error for org %s, user %s",
                    toolName, principal.getOrgId(), principal.getUserId());
            registry.counter("notifications.mcp.tool.error", "tool", toolName, "type", "connection").increment();
            throw new ToolCallException("Backend service unavailable, try again later");
        }
    }

    private static String httpErrorMessage(int status) {
        return switch (status) {
            case 403 -> "Access denied";
            case 404 -> "Resource not found";
            default -> status >= 400 && status < 500
                    ? "Invalid request (HTTP " + status + ")"
                    : "Backend service error, try again later";
        };
    }
}
