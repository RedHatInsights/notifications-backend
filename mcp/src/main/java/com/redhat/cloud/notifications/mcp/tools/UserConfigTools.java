package com.redhat.cloud.notifications.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.mcp.BackendRestClient;
import com.redhat.cloud.notifications.mcp.McpPrincipal;
import com.redhat.cloud.notifications.mcp.McpToolUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class UserConfigTools {

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    @RestClient
    BackendRestClient backendClient;

    @Inject
    MeterRegistry registry;

    @Inject
    ObjectMapper objectMapper;

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

    @Tool(description = "Saves user notification preference for a specific event type and subscription type. Updates whether the user is subscribed (true) or unsubscribed (false) for the given combination of bundle, application, event type, and subscription type.")
    public String saveUserNotificationPreferences(
            @NotBlank @ToolArg(description = "The name of the bundle") String bundleName,
            @NotBlank @ToolArg(description = "The name of the application") String applicationName,
            @NotBlank @ToolArg(description = "The name of the event type") String eventTypeName,
            @NotBlank @ToolArg(description = "The subscription type: INSTANT, DAILY, or DRAWER") String subscriptionType,
            @ToolArg(description = "Subscribe (true) or unsubscribe (false)", defaultValue = "true") boolean subscribe) {
        if (!subscriptionType.equals("INSTANT") && !subscriptionType.equals("DAILY") && !subscriptionType.equals("DRAWER")) {
            throw new ToolCallException("subscriptionType must be one of: INSTANT, DAILY, DRAWER");
        }

        String preferencesJson = buildPreferencesJson(bundleName, applicationName, eventTypeName, subscriptionType, subscribe);

        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        McpToolUtils.executeRestCall("saveUserNotificationPreferences", principal,
                () -> {
                    backendClient.saveUserNotificationPreferences(principal.getRawHeader(), preferencesJson);
                    return null;
                }, registry);
        return String.format("User notification preference %s successfully for %s/%s/%s (%s)",
                subscribe ? "enabled" : "disabled", bundleName, applicationName, eventTypeName, subscriptionType);
    }

    private String buildPreferencesJson(String bundleName, String applicationName, String eventTypeName, String subscriptionType, boolean subscribe) {
        Map<String, Boolean> subscriptionMap = new HashMap<>();
        subscriptionMap.put(subscriptionType, subscribe);

        Map<String, Object> eventTypeSettings = new HashMap<>();
        eventTypeSettings.put("emailSubscriptionTypes", subscriptionMap);

        Map<String, Object> eventTypes = new HashMap<>();
        eventTypes.put(eventTypeName, eventTypeSettings);

        Map<String, Object> applicationSettings = new HashMap<>();
        applicationSettings.put("eventTypes", eventTypes);

        Map<String, Object> applications = new HashMap<>();
        applications.put(applicationName, applicationSettings);

        Map<String, Object> bundleSettings = new HashMap<>();
        bundleSettings.put("applications", applications);

        Map<String, Object> bundles = new HashMap<>();
        bundles.put(bundleName, bundleSettings);

        Map<String, Object> root = new HashMap<>();
        root.put("bundles", bundles);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new ToolCallException("Failed to build preferences JSON: " + e.getMessage());
        }
    }
}
