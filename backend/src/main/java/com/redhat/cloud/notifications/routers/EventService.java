package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.db.EventResources;
import com.redhat.cloud.notifications.routers.models.EventLogEntry;
import com.redhat.cloud.notifications.routers.models.EventLogEntryAction;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.reactive.RestQuery;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.auth.rbac.RbacIdentityProvider.RBAC_READ_NOTIFICATIONS;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getAccountId;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(Constants.API_NOTIFICATIONS_V_1_0 + "/event")
public class EventService {

    public static final Pattern SORT_BY_PATTERN = Pattern.compile("^([a-z0-9_-]+):(asc|desc)$", CASE_INSENSITIVE);

    @Inject
    EventResources eventResources;

    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed(RBAC_READ_NOTIFICATIONS)
    @Operation(summary = "Retrieve the event log entries.")
    public Uni<List<EventLogEntry>> getEvents(@Context SecurityContext securityContext, @RestQuery Set<UUID> bundleIds, @RestQuery Set<UUID> appIds,
                                              @RestQuery String eventTypeName, @RestQuery LocalDate startDate, @RestQuery LocalDate endDate,
                                              @RestQuery Integer limit, @RestQuery Integer offset, @RestQuery String sortBy) {
        if (sortBy != null && !SORT_BY_PATTERN.matcher(sortBy).matches()) {
            throw new BadRequestException("Invalid sort_by query parameter");
        }
        return getAccountId(securityContext)
                .onItem().transformToUni(accountId ->
                        eventResources.get(accountId, bundleIds, appIds, eventTypeName, startDate, endDate, limit, offset, sortBy)
                )
                .onItem().transform(events ->
                        events.stream().map(event -> {
                            List<EventLogEntryAction> actions = event.getHistoryEntries().stream().map(historyEntry -> {
                                EventLogEntryAction action = new EventLogEntryAction();
                                action.setId(historyEntry.getId());
                                action.setEndpointType(historyEntry.getEndpoint().getType());
                                action.setInvocationResult(historyEntry.isInvocationResult());
                                return action;
                            }).collect(Collectors.toList());

                            EventLogEntry entry = new EventLogEntry();
                            entry.setId(event.getId());
                            entry.setCreated(event.getCreated());
                            entry.setBundle(event.getEventType().getApplication().getBundle().getDisplayName());
                            entry.setApplication(event.getEventType().getApplication().getDisplayName());
                            entry.setEventType(event.getEventType().getDisplayName());
                            entry.setActions(actions);
                            return entry;
                        }).collect(Collectors.toList())
                );
    }
}
