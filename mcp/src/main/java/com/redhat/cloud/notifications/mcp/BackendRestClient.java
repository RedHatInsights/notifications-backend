package com.redhat.cloud.notifications.mcp;

import com.redhat.cloud.notifications.mcp.dto.EndpointDTO;
import com.redhat.cloud.notifications.mcp.dto.EndpointTestRequestDTO;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@RegisterRestClient(configKey = "notifications-backend")
@Retry(delay = 1, delayUnit = ChronoUnit.SECONDS, maxRetries = 2, abortOn = ClientErrorException.class) // 1 initial + 2 retries = 3 attempts
public interface BackendRestClient {

    @GET
    @Path("/api/notifications/v2.0/notifications/severities")
    @Produces(APPLICATION_JSON)
    String getSeverities(@RestHeader("x-rh-identity") String xRhIdentity);

    @GET
    @Path("/api/notifications/v1.0/notifications/bundles/{bundleName}")
    @Produces(APPLICATION_JSON)
    String getBundle(@RestHeader("x-rh-identity") String xRhIdentity, @RestPath String bundleName);

    @GET
    @Path("/api/notifications/v1.0/notifications/bundles/{bundleName}/applications/{applicationName}")
    @Produces(APPLICATION_JSON)
    String getApplication(@RestHeader("x-rh-identity") String xRhIdentity, @RestPath String bundleName, @RestPath String applicationName);

    @GET
    @Path("/api/notifications/v1.0/notifications/bundles/{bundleName}/applications/{applicationName}/eventTypes/{eventTypeName}")
    @Produces(APPLICATION_JSON)
    String getEventType(@RestHeader("x-rh-identity") String xRhIdentity, @RestPath String bundleName, @RestPath String applicationName, @RestPath String eventTypeName);

    @GET
    @Path("/api/integrations/v2.0/endpoints")
    @Produces(APPLICATION_JSON)
    String getEndpoints(@RestHeader("x-rh-identity") String xRhIdentity, @RestQuery List<String> type, @RestQuery Boolean active, @RestQuery String name, @RestQuery Integer limit, @RestQuery Integer pageNumber);

    @GET
    @Path("/api/integrations/v2.0/endpoints/{id}")
    @Produces(APPLICATION_JSON)
    String getEndpoint(@RestHeader("x-rh-identity") String xRhIdentity, @RestPath UUID id);

    @GET
    @Path("/api/integrations/v2.0/endpoints/{id}/history")
    @Produces(APPLICATION_JSON)
    String getEndpointHistory(@RestHeader("x-rh-identity") String xRhIdentity, @RestPath UUID id, @RestQuery Boolean includeDetail, @RestQuery Integer limit, @RestQuery Integer pageNumber);

    @GET
    @Path("/api/integrations/v1.0/endpoints/{id}/history/{history_id}/details")
    @Produces(APPLICATION_JSON)
    String getEndpointHistoryDetails(@RestHeader("x-rh-identity") String xRhIdentity, @RestPath UUID id, @RestPath("history_id") UUID historyId);

    @GET
    @Path("/api/notifications/v1.0/notifications/events")
    @Produces(APPLICATION_JSON)
    String getEvents(@RestHeader("x-rh-identity") String xRhIdentity,
                     @RestQuery List<String> bundleIds,
                     @RestQuery List<String> appIds,
                     @RestQuery String eventTypeDisplayName,
                     @RestQuery String startDate,
                     @RestQuery String endDate,
                     @RestQuery List<String> endpointTypes,
                     @RestQuery List<String> invocationResults,
                     @RestQuery List<String> status,
                     @RestQuery Boolean includeDetails,
                     @RestQuery Boolean includePayload,
                     @RestQuery Boolean includeActions,
                     @RestQuery Integer limit,
                     @RestQuery Integer pageNumber);

    @GET
    @Path("/api/notifications/v1.0/org-config/daily-digest/time-preference")
    @Produces(APPLICATION_JSON)
    String getDailyDigestTimePreference(@RestHeader("x-rh-identity") String xRhIdentity);

    @GET
    @Path("/api/notifications/v1.0/user-config/notification-event-type-preference")
    @Produces(APPLICATION_JSON)
    String getUserNotificationPreferences(@RestHeader("x-rh-identity") String xRhIdentity);

    @GET
    @Path("/api/notifications/v1.0/user-config/notification-event-type-preference/{bundleName}/{applicationName}")
    @Produces(APPLICATION_JSON)
    String getUserNotificationPreferencesByApplication(@RestHeader("x-rh-identity") String xRhIdentity, @RestPath String bundleName, @RestPath String applicationName);

    @PUT
    @Path("/api/integrations/v1.0/endpoints/{id}/enable")
    void enableEndpoint(@RestHeader("x-rh-identity") String xRhIdentity, @RestPath UUID id);

    @DELETE
    @Path("/api/integrations/v1.0/endpoints/{id}/enable")
    void disableEndpoint(@RestHeader("x-rh-identity") String xRhIdentity, @RestPath UUID id);

    @POST
    @Path("/api/integrations/v1.0/endpoints/{uuid}/test")
    @Consumes(APPLICATION_JSON)
    @Retry(maxRetries = 0) // Non-idempotent POST that triggers a test notification; backend→engine layer already retries, so MCP-level retry would cascade
    void testEndpoint(@RestHeader("x-rh-identity") String xRhIdentity, @RestPath UUID uuid, EndpointTestRequestDTO requestBody);

    @PUT
    @Path("/api/notifications/v1.0/org-config/daily-digest/time-preference")
    @Consumes(APPLICATION_JSON)
    void setDailyDigestTimePreference(@RestHeader("x-rh-identity") String xRhIdentity, java.time.LocalTime time);

    @POST
    @Path("/api/notifications/v1.0/user-config/notification-event-type-preference")
    @Consumes(APPLICATION_JSON)
    void saveUserNotificationPreferences(@RestHeader("x-rh-identity") String xRhIdentity, String preferences);

    @DELETE
    @Path("/api/integrations/v1.0/endpoints/{id}")
    void deleteEndpoint(@RestHeader("x-rh-identity") String xRhIdentity, @RestPath UUID id);

    @POST
    @Path("/api/integrations/v1.0/endpoints")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Retry(maxRetries = 0) // Non-idempotent POST that creates a new integration; retry on transient failure would create duplicate endpoints
    String createEndpoint(@RestHeader("x-rh-identity") String xRhIdentity, EndpointDTO endpoint);

    @PUT
    @Path("/api/integrations/v1.0/endpoints/{id}")
    @Consumes(APPLICATION_JSON)
    void updateEndpoint(@RestHeader("x-rh-identity") String xRhIdentity, @RestPath UUID id, EndpointDTO endpoint);

    @GET
    @Path("/api/notifications/v1.0/notifications/eventTypes/{eventTypeId}/endpoints")
    @Produces(APPLICATION_JSON)
    String getLinkedEndpoints(@RestHeader("x-rh-identity") String xRhIdentity, @RestPath UUID eventTypeId, @RestQuery Integer limit, @RestQuery Integer offset);

    @PUT
    @Path("/api/notifications/v1.0/notifications/eventTypes/{eventTypeId}/endpoints")
    @Consumes(APPLICATION_JSON)
    void updateEventTypeEndpoints(@RestHeader("x-rh-identity") String xRhIdentity, @RestPath UUID eventTypeId, java.util.Set<UUID> endpointIds);

    @PUT
    @Path("/api/integrations/v1.0/endpoints/{endpointId}/eventType/{eventTypeId}")
    void addEventTypeToEndpoint(@RestHeader("x-rh-identity") String xRhIdentity, @RestPath UUID endpointId, @RestPath UUID eventTypeId);

    @DELETE
    @Path("/api/integrations/v1.0/endpoints/{endpointId}/eventType/{eventTypeId}")
    void deleteEventTypeFromEndpoint(@RestHeader("x-rh-identity") String xRhIdentity, @RestPath UUID endpointId, @RestPath UUID eventTypeId);

    @PUT
    @Path("/api/integrations/v1.0/endpoints/{endpointId}/eventTypes")
    @Consumes(APPLICATION_JSON)
    void updateEventTypesLinkedToEndpoint(@RestHeader("x-rh-identity") String xRhIdentity, @RestPath UUID endpointId, java.util.Set<UUID> eventTypeIds);
}
