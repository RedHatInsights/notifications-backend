package com.redhat.cloud.notifications.mcp.tools;

import com.redhat.cloud.notifications.mcp.BackendRestClient;
import com.redhat.cloud.notifications.mcp.McpPrincipal;
import com.redhat.cloud.notifications.mcp.McpToolUtils;
import com.redhat.cloud.notifications.mcp.dto.EndpointDTO;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Tool(description = "Enables an integration endpoint")
    public String enableIntegration(
            @NotBlank @ToolArg(description = "The UUID of the integration to enable") String id) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        McpToolUtils.executeRestCall("enableIntegration", principal,
                () -> {
                    backendClient.enableEndpoint(principal.getRawHeader(), McpToolUtils.parseUuid("id", id));
                    return null;
                }, registry);
        return "Integration enabled successfully";
    }

    @Tool(description = "Disables an integration endpoint")
    public String disableIntegration(
            @NotBlank @ToolArg(description = "The UUID of the integration to disable") String id) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        McpToolUtils.executeRestCall("disableIntegration", principal,
                () -> {
                    backendClient.disableEndpoint(principal.getRawHeader(), McpToolUtils.parseUuid("id", id));
                    return null;
                }, registry);
        return "Integration disabled successfully";
    }

    @Tool(description = "Tests an integration endpoint by sending a test notification")
    public String testIntegration(
            @NotBlank @ToolArg(description = "The UUID of the integration to test") String uuid) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        McpToolUtils.executeRestCall("testIntegration", principal,
                () -> {
                    backendClient.testEndpoint(principal.getRawHeader(), McpToolUtils.parseUuid("uuid", uuid));
                    return null;
                }, registry);
        return "Test notification sent successfully";
    }

    @Tool(description = "Deletes an integration endpoint. Note: You cannot delete system endpoints (email_subscription, drawer).")
    public String deleteIntegration(
            @NotBlank @ToolArg(description = "The UUID of the integration to delete") String id) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        McpToolUtils.executeRestCall("deleteIntegration", principal,
                () -> {
                    backendClient.deleteEndpoint(principal.getRawHeader(), McpToolUtils.parseUuid("id", id));
                    return null;
                }, registry);
        return "Integration deleted successfully";
    }

    @Tool(description = """
        Creates a new integration endpoint. Returns the created endpoint as JSON including its UUID.

        The endpoint parameter uses polymorphic properties based on the type field:
        - type=WEBHOOK or ANSIBLE: properties is WebhookPropertiesDTO
        - type=CAMEL: properties is CamelPropertiesDTO (requires sub_type: slack, teams, google_chat, servicenow, or splunk)
        - type=PAGERDUTY: properties is PagerDutyPropertiesDTO
        - type=DRAWER or EMAIL_SUBSCRIPTION: properties is SystemSubscriptionPropertiesDTO (system endpoints, rarely created via API)

        Property field names use snake_case (e.g., disable_ssl_verification, secret_token).
        See the EndpointDTO schema for complete structure including required fields per type.
        """)
    public String createIntegration(
            @NotNull @Valid @ToolArg(description = "Endpoint configuration") EndpointDTO endpoint) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return McpToolUtils.executeRestCall("createIntegration", principal,
                () -> backendClient.createEndpoint(principal.getRawHeader(), endpoint), registry);
    }

    @Tool(description = """
        Updates an existing integration endpoint. The endpoint configuration replaces the existing configuration,
        so all fields (name, description, type, enabled, properties) should be provided.

        The endpoint parameter uses polymorphic properties - see createIntegration description for type/properties mapping.
        """)
    public String updateIntegration(
            @NotBlank @ToolArg(description = "The UUID of the integration to update") String id,
            @NotNull @Valid @ToolArg(description = "Updated endpoint configuration") EndpointDTO endpoint) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        McpToolUtils.executeRestCall("updateIntegration", principal,
                () -> {
                    backendClient.updateEndpoint(principal.getRawHeader(), McpToolUtils.parseUuid("id", id), endpoint);
                    return null;
                }, registry);
        return "Integration updated successfully";
    }

    @Tool(description = """
            Adds a link between an integration and an event type. This allows the integration to receive \
            notifications when this event occurs. This is an incremental operation - it adds one event type \
            without affecting existing event type associations.
            """)
    public String addEventTypeToIntegration(
            @NotBlank @ToolArg(description = "UUID of the integration") String endpointId,
            @NotBlank @ToolArg(description = "UUID of the event type to link") String eventTypeId) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        UUID endpointUuid = McpToolUtils.parseUuid("endpointId", endpointId);
        UUID eventTypeUuid = McpToolUtils.parseUuid("eventTypeId", eventTypeId);

        McpToolUtils.executeRestCall("addEventTypeToIntegration", principal,
                () -> {
                    backendClient.addEventTypeToEndpoint(principal.getRawHeader(), endpointUuid, eventTypeUuid);
                    return null;
                }, registry);

        return "Event type linked to integration successfully.";
    }

    @Tool(description = """
            Removes the link between an integration and an event type. This stops the integration from \
            receiving notifications for this event type. This is an incremental operation - it removes one \
            event type association without affecting other linked event types.
            """)
    public String deleteEventTypeFromIntegration(
            @NotBlank @ToolArg(description = "UUID of the integration") String endpointId,
            @NotBlank @ToolArg(description = "UUID of the event type to unlink") String eventTypeId) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        UUID endpointUuid = McpToolUtils.parseUuid("endpointId", endpointId);
        UUID eventTypeUuid = McpToolUtils.parseUuid("eventTypeId", eventTypeId);

        McpToolUtils.executeRestCall("deleteEventTypeFromIntegration", principal,
                () -> {
                    backendClient.deleteEventTypeFromEndpoint(principal.getRawHeader(), endpointUuid, eventTypeUuid);
                    return null;
                }, registry);

        return "Event type unlinked from integration successfully.";
    }

    @Tool(description = """
            Updates the complete list of event types associated with an integration. This controls which events \
            will trigger notifications to this integration. Pass an empty set to remove all event type associations. \
            Pass a set of event type UUIDs to route notifications for those specific events. This operation \
            replaces the existing event type configuration entirely.
            """)
    public String updateEventTypesLinkedToIntegration(
            @NotBlank @ToolArg(description = "UUID of the integration") String endpointId,
            @ToolArg(description = "Set of event type UUIDs to associate (empty set removes all associations)") Set<String> eventTypeIds) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        UUID endpointUuid = McpToolUtils.parseUuid("endpointId", endpointId);
        Set<UUID> eventTypeUuids = eventTypeIds.stream()
                .map(id -> McpToolUtils.parseUuid("eventTypeId", id))
                .collect(Collectors.toSet());

        McpToolUtils.executeRestCall("updateEventTypesLinkedToIntegration", principal,
                () -> {
                    backendClient.updateEventTypesLinkedToEndpoint(principal.getRawHeader(), endpointUuid, eventTypeUuids);
                    return null;
                }, registry);

        return "Integration event type associations updated successfully.";
    }
}
