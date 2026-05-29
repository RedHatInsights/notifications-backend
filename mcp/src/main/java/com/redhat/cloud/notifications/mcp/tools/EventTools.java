package com.redhat.cloud.notifications.mcp.tools;

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
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class EventTools {

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    @RestClient
    BackendRestClient backendClient;

    @Inject
    MeterRegistry registry;

    @Tool(description = "Retrieves notification event log entries. Returns a paginated list with fields: id, bundle, application, event_type, created, severity. By default, actions and payload are omitted — set includeActions=true to see delivery status per integration, and includePayload=true to see event content. Use the getBundle or getApplication tools first to obtain bundle/application UUIDs for filtering.")
    public String getEvents(
            @ToolArg(description = "Filter by bundle UUIDs (use getBundle to find UUIDs)", required = false) List<String> bundleIds,
            @ToolArg(description = "Filter by application UUIDs (use getApplication to find UUIDs)", required = false) List<String> appIds,
            @ToolArg(description = "Filter by event type display name", required = false) String eventTypeDisplayName,
            @ToolArg(description = "Filter events from this date (yyyy-MM-dd)", required = false) String startDate,
            @ToolArg(description = "Filter events until this date (yyyy-MM-dd)", required = false) String endDate,
            @ToolArg(description = "Filter by endpoint types: webhook, email_subscription, camel, ansible, drawer, pagerduty. Camel subtypes use colon notation: camel:slack, camel:teams, camel:google_chat, camel:splunk, camel:servicenow", required = false) List<String> endpointTypes,
            @ToolArg(description = "Filter by invocation result as string values: 'true' for success, 'false' for failure", required = false) List<String> invocationResults,
            @ToolArg(description = "Filter by notification status: SUCCESS, SENT, FAILED, PROCESSING", required = false) List<String> status,
            @ToolArg(description = "Include detailed information about each notification action (default: false)", required = false) Boolean includeDetails,
            @ToolArg(description = "Include the event payload in the response (default: false)", required = false) Boolean includePayload,
            @ToolArg(description = "Include notification actions (delivery attempts per integration) in the response (default: false)", required = false) Boolean includeActions,
            @ToolArg(description = "Number of items per page", required = false, defaultValue = "20") Integer limit,
            @ToolArg(description = "Page number (starts at 0)", required = false) Integer pageNumber) {
        LocalDate parsedStart = McpToolUtils.parseDate("startDate", startDate);
        LocalDate parsedEnd = McpToolUtils.parseDate("endDate", endDate);
        if (parsedStart != null && parsedEnd != null && parsedStart.isAfter(parsedEnd)) {
            throw new ToolCallException("startDate must not be after endDate");
        }
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return McpToolUtils.executeRestCall("getEvents", principal,
                () -> backendClient.getEvents(principal.getRawHeader(), bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, invocationResults, status, includeDetails, includePayload, includeActions, limit, pageNumber), registry);
    }
}
