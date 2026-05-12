package com.redhat.cloud.notifications.mcp;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.UUID;
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

    // Cached here because severities are public, rarely updated, and require no ownership check.
    @CacheResult(cacheName = "mcp-get-severities")
    @Tool(description = "Returns the list of available notification severities")
    public String getSeverities() {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return executeRestCall("getSeverities", principal,
                () -> backendClient.getSeverities(principal.getRawHeader()));
    }

    // Cached here because bundles are public, rarely updated, and require no ownership check.
    @CacheResult(cacheName = "mcp-get-bundle")
    @Tool(description = "Retrieves a bundle by name")
    public String getBundle(
            @NotBlank @ToolArg(description = "The name of the bundle") String bundleName) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return executeRestCall("getBundle", principal,
                () -> backendClient.getBundle(principal.getRawHeader(), bundleName));
    }

    // Cached here because applications are public, rarely updated, and require no ownership check.
    @CacheResult(cacheName = "mcp-get-application")
    @Tool(description = "Retrieves an application by bundle and application name")
    public String getApplication(
            @NotBlank @ToolArg(description = "The name of the bundle") String bundleName,
            @NotBlank @ToolArg(description = "The name of the application") String applicationName) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return executeRestCall("getApplication", principal,
                () -> backendClient.getApplication(principal.getRawHeader(), bundleName, applicationName));
    }

    // Cached here because event types are public, rarely updated, and require no ownership check.
    @CacheResult(cacheName = "mcp-get-event-type")
    @Tool(description = "Retrieves an event type by bundle, application, and event type name")
    public String getEventType(
            @NotBlank @ToolArg(description = "The name of the bundle") String bundleName,
            @NotBlank @ToolArg(description = "The name of the application") String applicationName,
            @NotBlank @ToolArg(description = "The name of the event type") String eventTypeName) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return executeRestCall("getEventType", principal,
                () -> backendClient.getEventType(principal.getRawHeader(), bundleName, applicationName, eventTypeName));
    }

    @Tool(description = "Lists all integrations with optional filtering by type, active status, or name")
    public String getIntegrations(
            @ToolArg(description = "Filter by integration type: webhook, email_subscription, camel, ansible, drawer, pagerduty. Camel subtypes use colon notation: camel:slack, camel:teams, camel:google_chat, camel:splunk, camel:servicenow", required = false) List<String> type,
            @ToolArg(description = "Filter by active status", required = false) Boolean active,
            @ToolArg(description = "Filter by integration name", required = false) String name,
            @ToolArg(description = "Number of items per page", required = false, defaultValue = "20") Integer limit,
            @ToolArg(description = "Page number (starts at 0)", required = false) Integer pageNumber) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return executeRestCall("getIntegrations", principal,
                () -> backendClient.getEndpoints(principal.getRawHeader(), type, active, name, limit, pageNumber));
    }

    @Tool(description = "Retrieves a specific integration by ID")
    public String getIntegration(
            @NotBlank @ToolArg(description = "The UUID of the integration") String id) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return executeRestCall("getIntegration", principal,
                () -> backendClient.getEndpoint(principal.getRawHeader(), parseUuid("id", id)));
    }

    @Tool(description = "Retrieves notification history for an integration")
    public String getIntegrationHistory(
            @NotBlank @ToolArg(description = "The UUID of the integration") String id,
            @ToolArg(description = "Include detailed information in the reply", required = false) Boolean includeDetail,
            @ToolArg(description = "Number of items per page", required = false, defaultValue = "20") Integer limit,
            @ToolArg(description = "Page number (starts at 0)", required = false) Integer pageNumber) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return executeRestCall("getIntegrationHistory", principal,
                () -> backendClient.getEndpointHistory(principal.getRawHeader(), parseUuid("id", id), includeDetail, limit, pageNumber));
    }

    @Tool(description = "Retrieves detailed information about a specific integration notification event")
    public String getIntegrationHistoryDetails(
            @NotBlank @ToolArg(description = "The UUID of the integration") String integrationId,
            @NotBlank @ToolArg(description = "The UUID of the notification history event") String historyId) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return executeRestCall("getIntegrationHistoryDetails", principal,
                () -> backendClient.getEndpointHistoryDetails(principal.getRawHeader(), parseUuid("integrationId", integrationId), parseUuid("historyId", historyId)));
    }

    private static UUID parseUuid(String paramName, String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ToolCallException("Invalid UUID for " + paramName + ": " + value);
        }
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
