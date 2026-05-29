package com.redhat.cloud.notifications.mcp.tools;

import com.redhat.cloud.notifications.mcp.BackendRestClient;
import com.redhat.cloud.notifications.mcp.McpPrincipal;
import com.redhat.cloud.notifications.mcp.McpToolUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class UserConfigTools {

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    @RestClient
    BackendRestClient backendClient;

    @Inject
    MeterRegistry registry;

    @Tool(description = "Retrieves user notification preferences for all bundles and applications. Returns a nested structure: bundles → applications → event types, each showing which subscription types (instant, daily, drawer) the user is subscribed to. Use getUserNotificationPreferencesByApplication for a single application.")
    public String getUserNotificationPreferences() {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return McpToolUtils.executeRestCall("getUserNotificationPreferences", principal,
                () -> backendClient.getUserNotificationPreferences(principal.getRawHeader()), registry);
    }

    @Tool(description = "Retrieves user notification preferences for a specific bundle and application. Returns event types with their subscription types (instant, daily, drawer) and whether the user is subscribed. Lighter than getUserNotificationPreferences when you only need one application.")
    public String getUserNotificationPreferencesByApplication(
            @NotBlank @ToolArg(description = "The name of the bundle") String bundleName,
            @NotBlank @ToolArg(description = "The name of the application") String applicationName) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return McpToolUtils.executeRestCall("getUserNotificationPreferencesByApplication", principal,
                () -> backendClient.getUserNotificationPreferencesByApplication(principal.getRawHeader(), bundleName, applicationName), registry);
    }
}
