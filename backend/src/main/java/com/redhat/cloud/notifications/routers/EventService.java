package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.EventResources;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.routers.models.EventLogEntry;
import com.redhat.cloud.notifications.routers.models.EventLogEntryAction;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import com.redhat.cloud.notifications.routers.models.PageLinksBuilder;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.resteasy.reactive.RestQuery;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.auth.rbac.RbacIdentityProvider.RBAC_READ_NOTIFICATIONS;
import static com.redhat.cloud.notifications.routers.EventService.PATH;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getAccountId;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(PATH)
public class EventService {

    public static final String PATH = API_NOTIFICATIONS_V_1_0 + "/notifications/events";
    public static final Pattern SORT_BY_PATTERN = Pattern.compile("^([a-z0-9_-]+):(asc|desc)$", CASE_INSENSITIVE);

    @Inject
    EventResources eventResources;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed(RBAC_READ_NOTIFICATIONS)
    @Operation(summary = "Retrieve the event log entries.")
    public Uni<Page<EventLogEntry>> getEvents(@Context SecurityContext securityContext, @RestQuery Set<UUID> bundleIds, @RestQuery Set<UUID> appIds,
                                              @RestQuery String eventTypeDisplayName, @RestQuery LocalDate startDate, @RestQuery LocalDate endDate,
                                              @RestQuery Set<EndpointType> endpointTypes, @RestQuery Set<Boolean> invocationResults,
                                              @RestQuery @DefaultValue("10") int limit, @RestQuery @DefaultValue("0") int offset, @RestQuery String sortBy) {
        if (limit < 1 || limit > 200) {
            throw new BadRequestException("Invalid 'limit' query parameter, its value must be between 1 and 200");
        }
        if (sortBy != null && !SORT_BY_PATTERN.matcher(sortBy).matches()) {
            throw new BadRequestException("Invalid 'sortBy' query parameter");
        }
        return sessionFactory.withSession(session -> {
            return getAccountId(securityContext)
                    .onItem().transformToUni(accountId ->
                            eventResources.getEvents(accountId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, invocationResults, limit, offset, sortBy)
                                    .onItem().transform(events ->
                                            events.stream().map(event -> {
                                                List<EventLogEntryAction> actions = event.getHistoryEntries().stream().map(historyEntry -> {
                                                    EventLogEntryAction action = new EventLogEntryAction();
                                                    action.setId(historyEntry.getId());
                                                    action.setEndpointType(historyEntry.getEndpointType());
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
                                    )
                                    .onItem().transformToUni(entries ->
                                            eventResources.count(accountId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, invocationResults)
                                                    .onItem().transform(count -> {
                                                        Meta meta = new Meta();
                                                        meta.setCount(count);

                                                        Map<String, String> links = PageLinksBuilder.build(PATH, count, limit, offset);

                                                        Page<EventLogEntry> page = new Page<>();
                                                        page.setData(entries);
                                                        page.setMeta(meta);
                                                        page.setLinks(links);
                                                        return page;
                                                    })
                                    )
                    );
        });
    }
}
