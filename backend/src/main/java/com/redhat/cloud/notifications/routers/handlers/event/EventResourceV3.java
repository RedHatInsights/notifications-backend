package com.redhat.cloud.notifications.routers.handlers.event;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.annotation.Authorization;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.routers.models.EventLogEntry;
import com.redhat.cloud.notifications.routers.models.EventLogEntryActionStatus;
import com.redhat.cloud.notifications.routers.models.Page;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.resteasy.reactive.RestQuery;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_3_0;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.EVENTS_VIEW;
import static com.redhat.cloud.notifications.db.Query.DEFAULT_RESULTS_PER_PAGE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

public class EventResourceV3 extends EventResourceCommon {

    @Path(API_NOTIFICATIONS_V_3_0 + "/notifications/events")
    public static class V3 extends EventResourceV3 {
    }

    @GET
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve the event log entries", description = "Retrieves the event log entries. Use this endpoint to review a full history of the events related to the tenant. You can sort by the bundle, application, event, and created fields. You can specify the sort order by appending :asc or :desc to the field, for example bundle:desc. Sorting defaults to desc for the created field and to asc for all other fields."
    )
    @Parameters({
        @Parameter(
            name = "limit",
            in = ParameterIn.QUERY,
            description = "Number of items per page, if not specified " + DEFAULT_RESULTS_PER_PAGE + " is used.",
            schema = @Schema(type = SchemaType.INTEGER, defaultValue = DEFAULT_RESULTS_PER_PAGE + "")
        ),
        @Parameter(
            name = "startDate",
            in = ParameterIn.QUERY,
            description = "Start of the date range filter. Accepts date-time (yyyy-MM-dd'T'HH:mm:ss).",
            schema = @Schema(type = SchemaType.STRING, format = "date-time")
        ),
        @Parameter(
            name = "endDate",
            in = ParameterIn.QUERY,
            description = "End of the date range filter. Accepts date-time (yyyy-MM-dd'T'HH:mm:ss).",
            schema = @Schema(type = SchemaType.STRING, format = "date-time")
        )
    })
    @APIResponses(value = {
        @APIResponse(responseCode = "200", content = @Content(mediaType = APPLICATION_JSON)),
        @APIResponse(responseCode = "400", content = @Content(mediaType = TEXT_PLAIN),
                description = "Invalid query parameter value (unknown endpoint type, invalid status filter, or malformed date).")
    })
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS_EVENTS, workspacePermissions = EVENTS_VIEW, resourceType = "event")
    public Page<EventLogEntry> getEvents(@Context SecurityContext securityContext, @Context UriInfo uriInfo,
                                         @RestQuery Set<UUID> bundleIds, @RestQuery Set<UUID> appIds,
                                         @RestQuery String eventTypeDisplayName, @RestQuery LocalDateTime startDateTime, @RestQuery LocalDateTime endDateTime,
                                         @RestQuery Set<String> endpointTypes, @RestQuery Set<Boolean> invocationResults,
                                         @RestQuery Set<EventLogEntryActionStatus> status, @RestQuery Set<Severity> severities,
                                         @BeanParam @Valid Query query,
                                         @RestQuery boolean includeDetails, @RestQuery boolean includePayload, @RestQuery boolean includeActions) {
        return doGetEvents(securityContext, uriInfo, bundleIds, appIds, eventTypeDisplayName, startDateTime, endDateTime,
            endpointTypes, invocationResults, status, severities, query, includeDetails, includePayload, includeActions);
    }
}
