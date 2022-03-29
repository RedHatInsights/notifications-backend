package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.routers.models.EventLogEntry;
import com.redhat.cloud.notifications.routers.models.EventLogEntryAction;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import com.redhat.cloud.notifications.routers.models.PageLinksBuilder;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.reactive.RestQuery;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS_EVENTS;
import static com.redhat.cloud.notifications.routers.EventResource.PATH;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getAccountId;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(PATH)
public class EventResource {

    public static final String PATH = API_NOTIFICATIONS_V_1_0 + "/notifications/events";
    public static final Pattern SORT_BY_PATTERN = Pattern.compile("^([a-z0-9_-]+):(asc|desc)$", CASE_INSENSITIVE);

    @Inject
    EventRepository eventRepository;

    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed(RBAC_READ_NOTIFICATIONS_EVENTS)
    @Operation(summary = "Retrieve the event log entries.")
    public Page<EventLogEntry> getEvents(@Context SecurityContext securityContext, @RestQuery Set<UUID> bundleIds, @RestQuery Set<UUID> appIds,
                                              @RestQuery String eventTypeDisplayName, @RestQuery LocalDate startDate, @RestQuery LocalDate endDate,
                                              @RestQuery Set<EndpointType> endpointTypes, @RestQuery Set<Boolean> invocationResults,
                                              @RestQuery @DefaultValue("10") int limit, @RestQuery @DefaultValue("0") int offset, @RestQuery String sortBy,
                                              @RestQuery boolean includeDetails, @RestQuery boolean includePayload, @RestQuery boolean includeActions) {
        if (limit < 1 || limit > 200) {
            throw new BadRequestException("Invalid 'limit' query parameter, its value must be between 1 and 200");
        }
        if (sortBy != null && !SORT_BY_PATTERN.matcher(sortBy).matches()) {
            throw new BadRequestException("Invalid 'sortBy' query parameter");
        }
        String accountId = getAccountId(securityContext);
        List<Event> events = eventRepository.getEvents(accountId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, invocationResults, includeActions, limit, offset, sortBy);
        List<EventLogEntry> eventLogEntries = getEntriesForEvents(events, includeActions, includeDetails, includePayload);
        Long count = eventRepository.count(accountId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, invocationResults);

        Meta meta = new Meta();
        meta.setCount(count);

        Map<String, String> links = PageLinksBuilder.build(PATH, count, limit, offset);

        Page<EventLogEntry> page = new Page<>();
        page.setData(eventLogEntries);
        page.setMeta(meta);
        page.setLinks(links);
        return page;
    }

    @GET
    @Path("/{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(RBAC_READ_NOTIFICATIONS_EVENTS)
    @Operation(summary = "Retrieve the event log for one event")
    public List<EventLogEntry> getEventLogById(@Context SecurityContext securityContext, @PathParam("id") UUID id) {

        List<Event> events = new ArrayList<>();
        Event dummy = eventRepository.getEventById(id, true);
        events.add(dummy);

        boolean includeActions = true;
        boolean includeDetails = true;
        boolean includePayload = true;
        List<EventLogEntry> entries = getEntriesForEvents(events, includeActions, includeDetails, includePayload);

        return entries;
    }

    private List<EventLogEntry> getEntriesForEvents(List<Event> events, boolean includeActions, boolean includeDetails, boolean includePayload) {
        List<EventLogEntry> eventLogEntries = events.stream().map(event -> {
            List<EventLogEntryAction> actions;
            if (!includeActions) {
                actions = Collections.emptyList();
            } else {
                actions = event.getHistoryEntries().stream().map(historyEntry -> {
                    EventLogEntryAction action = new EventLogEntryAction();
                    action.setId(historyEntry.getId());
                    action.setEndpointId(historyEntry.getEndpointId());
                    action.setEndpointType(historyEntry.getEndpointType());
                    action.setEndpointSubType(historyEntry.getEndpointSubType());
                    action.setInvocationResult(historyEntry.isInvocationResult());
                    if (includeDetails) {
                        action.setDetails(historyEntry.getDetails());
                    }
                    return action;
                }).collect(Collectors.toList());
            }

            EventLogEntry entry = new EventLogEntry();
            entry.setId(event.getId());
            entry.setCreated(event.getCreated());
            entry.setBundle(event.getBundleDisplayName());
            entry.setApplication(event.getApplicationDisplayName());
            entry.setEventType(event.getEventTypeDisplayName());
            entry.setActions(actions);
            if (includePayload) {
                entry.setPayload(event.getPayload());
            }
            return entry;
        }).collect(Collectors.toList());

        return eventLogEntries;
    }


}
