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

import java.util.List;

@ApplicationScoped
public class IntegrationTools {

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    @RestClient
    BackendRestClient backendClient;

    @Inject
    MeterRegistry registry;

    @Tool(description = "Lists all integrations with optional filtering by type, active status, or name")
    public String getIntegrations(
            @ToolArg(description = "Filter by integration type: webhook, email_subscription, camel, ansible, drawer, pagerduty. Camel subtypes use colon notation: camel:slack, camel:teams, camel:google_chat, camel:splunk, camel:servicenow", required = false) List<String> type,
            @ToolArg(description = "Filter by active status", required = false) Boolean active,
            @ToolArg(description = "Filter by integration name", required = false) String name,
            @ToolArg(description = "Number of items per page", required = false, defaultValue = "20") Integer limit,
            @ToolArg(description = "Page number (starts at 0)", required = false) Integer pageNumber) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return McpToolUtils.executeRestCall("getIntegrations", principal,
                () -> backendClient.getEndpoints(principal.getRawHeader(), type, active, name, limit, pageNumber), registry);
    }

    @Tool(description = "Retrieves a specific integration by ID")
    public String getIntegration(
            @NotBlank @ToolArg(description = "The UUID of the integration") String id) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return McpToolUtils.executeRestCall("getIntegration", principal,
                () -> backendClient.getEndpoint(principal.getRawHeader(), McpToolUtils.parseUuid("id", id)), registry);
    }

    @Tool(description = "Retrieves notification history for an integration")
    public String getIntegrationHistory(
            @NotBlank @ToolArg(description = "The UUID of the integration") String id,
            @ToolArg(description = "Include detailed information in the reply", required = false) Boolean includeDetail,
            @ToolArg(description = "Number of items per page", required = false, defaultValue = "20") Integer limit,
            @ToolArg(description = "Page number (starts at 0)", required = false) Integer pageNumber) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return McpToolUtils.executeRestCall("getIntegrationHistory", principal,
                () -> backendClient.getEndpointHistory(principal.getRawHeader(), McpToolUtils.parseUuid("id", id), includeDetail, limit, pageNumber), registry);
    }

    @Tool(description = "Retrieves detailed information about a specific integration notification event")
    public String getIntegrationHistoryDetails(
            @NotBlank @ToolArg(description = "The UUID of the integration") String integrationId,
            @NotBlank @ToolArg(description = "The UUID of the notification history event") String historyId) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return McpToolUtils.executeRestCall("getIntegrationHistoryDetails", principal,
                () -> backendClient.getEndpointHistoryDetails(principal.getRawHeader(), McpToolUtils.parseUuid("integrationId", integrationId), McpToolUtils.parseUuid("historyId", historyId)), registry);
    }
}
