package com.redhat.cloud.notifications.mcp.tools;

import com.redhat.cloud.notifications.mcp.BackendRestClient;
import com.redhat.cloud.notifications.mcp.McpPrincipal;
import com.redhat.cloud.notifications.mcp.McpToolUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkus.cache.CacheResult;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class NotificationTools {

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    @RestClient
    BackendRestClient backendClient;

    @Inject
    MeterRegistry registry;

    // Cached here because severities are public, rarely updated, and require no ownership check.
    @CacheResult(cacheName = "mcp-get-severities")
    @Tool(description = "Returns the list of available notification severities")
    public String getSeverities() {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return McpToolUtils.executeRestCall("getSeverities", principal,
                () -> backendClient.getSeverities(principal.getRawHeader()), registry);
    }

    // Cached here because bundles are public, rarely updated, and require no ownership check.
    @CacheResult(cacheName = "mcp-get-bundle")
    @Tool(description = "Retrieves a bundle by name")
    public String getBundle(
            @NotBlank @ToolArg(description = "The name of the bundle") String bundleName) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return McpToolUtils.executeRestCall("getBundle", principal,
                () -> backendClient.getBundle(principal.getRawHeader(), bundleName), registry);
    }

    // Cached here because applications are public, rarely updated, and require no ownership check.
    @CacheResult(cacheName = "mcp-get-application")
    @Tool(description = "Retrieves an application by bundle and application name")
    public String getApplication(
            @NotBlank @ToolArg(description = "The name of the bundle") String bundleName,
            @NotBlank @ToolArg(description = "The name of the application") String applicationName) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return McpToolUtils.executeRestCall("getApplication", principal,
                () -> backendClient.getApplication(principal.getRawHeader(), bundleName, applicationName), registry);
    }

    // Cached here because event types are public, rarely updated, and require no ownership check.
    @CacheResult(cacheName = "mcp-get-event-type")
    @Tool(description = "Retrieves an event type by bundle, application, and event type name")
    public String getEventType(
            @NotBlank @ToolArg(description = "The name of the bundle") String bundleName,
            @NotBlank @ToolArg(description = "The name of the application") String applicationName,
            @NotBlank @ToolArg(description = "The name of the event type") String eventTypeName) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return McpToolUtils.executeRestCall("getEventType", principal,
                () -> backendClient.getEventType(principal.getRawHeader(), bundleName, applicationName, eventTypeName), registry);
    }

    @Tool(description = """
            Retrieves the integrations linked to an event type. Returns a paginated list of integrations that will be \
            triggered when this event occurs. Use this to verify the current notification routing configuration before \
            making changes with updateEventTypeEndpoints.
            """)
    public String getLinkedIntegrations(
            @NotBlank @ToolArg(description = "UUID of the event type") String eventTypeId,
            @ToolArg(description = "Maximum number of results per page (default 50)", required = false) Integer limit,
            @ToolArg(description = "Offset for pagination (default 0)", required = false) Integer offset) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        UUID eventTypeUuid = McpToolUtils.parseUuid("eventTypeId", eventTypeId);
        return McpToolUtils.executeRestCall("getLinkedIntegrations", principal,
                () -> backendClient.getLinkedEndpoints(principal.getRawHeader(), eventTypeUuid, limit, offset), registry);
    }

    @Tool(description = """
            Updates the list of integrations associated with an event type. This controls which integrations will be \
            triggered when this event occurs. Pass an empty set to disable all notifications for this event type. \
            Pass a set of integration UUIDs to route notifications to those specific integrations. This operation \
            replaces the existing integration configuration entirely.
            """)
    public String updateEventTypeIntegrations(
            @NotBlank @ToolArg(description = "UUID of the event type") String eventTypeId,
            @ToolArg(description = "Set of integration UUIDs to associate (empty set disables notifications)") Set<String> endpointIds) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        UUID eventTypeUuid = McpToolUtils.parseUuid("eventTypeId", eventTypeId);
        if (endpointIds == null) {
            throw new ToolCallException("Integration UUIDs Ids cannot be null");
        }
        Set<UUID> endpointUuids = endpointIds.stream()
                .map(id -> McpToolUtils.parseUuid("endpointId", id))
                .collect(Collectors.toSet());

        McpToolUtils.executeRestCall("updateEventTypeIntegrations", principal,
                () -> {
                    backendClient.updateEventTypeEndpoints(principal.getRawHeader(), eventTypeUuid, endpointUuids);
                    return null;
                }, registry);

        return "Event type endpoint associations updated successfully.";
    }
}
