package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.db.repositories.SubscriptionChangeLogRepository;
import com.redhat.cloud.notifications.routers.internal.models.dto.SubscriptionUnsubscribeStatDTO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;

@RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
@Path(API_INTERNAL + "/subscription-audit")
public class SubscriptionAuditResource {

    @Inject
    SubscriptionChangeLogRepository subscriptionChangeLogRepository;

    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Retrieve unsubscription statistics per event type since a given date")
    @APIResponse(responseCode = "200", description = "Unsubscription statistics retrieved successfully")
    @APIResponse(responseCode = "400", description = "Missing or invalid 'since' query parameter")
    public List<SubscriptionUnsubscribeStatDTO> getUnsubscribeStats(@QueryParam("since") String since) {
        if (since == null || since.isBlank()) {
            throw new BadRequestException("The 'since' query parameter is required");
        }

        OffsetDateTime sinceDate;
        try {
            sinceDate = OffsetDateTime.parse(since);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Invalid ISO-8601 format for 'since' parameter: " + since);
        }

        List<Object[]> results = subscriptionChangeLogRepository.countUnsubscribesByEventType(sinceDate);

        return results.stream().map(row -> {
            SubscriptionUnsubscribeStatDTO dto = new SubscriptionUnsubscribeStatDTO();
            dto.eventTypeId = (UUID) row[0];
            dto.eventTypeDisplayName = (String) row[1];
            dto.unsubscribeCount = ((Number) row[2]).longValue();
            return dto;
        }).toList();
    }
}
