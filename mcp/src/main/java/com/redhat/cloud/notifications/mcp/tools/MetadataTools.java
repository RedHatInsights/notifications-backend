package com.redhat.cloud.notifications.mcp.tools;

import com.redhat.cloud.notifications.mcp.BackendRestClient;
import com.redhat.cloud.notifications.mcp.McpPrincipal;
import com.redhat.cloud.notifications.mcp.McpToolUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.cache.CacheResult;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class MetadataTools {

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
}
