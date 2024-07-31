package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.routers.models.EventLogEntry;
import com.redhat.cloud.notifications.routers.models.EventLogEntryAction;
import com.redhat.cloud.notifications.routers.models.EventLogEntryActionStatus;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import com.redhat.cloud.notifications.routers.models.PageLinksBuilder;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.reactive.RestQuery;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_2_0;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS_EVENTS;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class EventResource {

    @Inject
    EventRepository eventRepository;


    @Path(API_NOTIFICATIONS_V_1_0 + "/notifications/events")
    public static class V1 extends EventResource {

    }

    @Path(API_NOTIFICATIONS_V_2_0 + "/notifications/events")
    public static class V2 extends EventResource {

    }

    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed(RBAC_READ_NOTIFICATIONS_EVENTS)
    @Operation(summary = "Retrieve the event log entries", description = "Retrieves the event log entries. Use this endpoint to review a full history of the events related to the tenant. You can sort by the bundle, application, event, and created fields. You can specify the sort order by appending :asc or :desc to the field, for example bundle:desc. Sorting defaults to desc for the created field and to asc for all other fields."
    )
    public Page<EventLogEntry> getEvents(@Context SecurityContext securityContext, @Context UriInfo uriInfo,
                                         @RestQuery Set<UUID> bundleIds, @RestQuery Set<UUID> appIds,
                                         @RestQuery String eventTypeDisplayName, @RestQuery LocalDate startDate, @RestQuery LocalDate endDate,
                                         @RestQuery Set<String> endpointTypes, @RestQuery Set<Boolean> invocationResults,
                                         @RestQuery Set<EventLogEntryActionStatus> status,
                                         @BeanParam @Valid Query query,
                                         @RestQuery boolean includeDetails, @RestQuery boolean includePayload, @RestQuery boolean includeActions) {
        Set<EndpointType> basicTypes = Collections.emptySet();
        Set<CompositeEndpointType> compositeTypes = Collections.emptySet();
        Set<NotificationStatus> notificationStatusSet = status == null ? Set.of() : toNotificationStatus(status);

        if (endpointTypes != null && endpointTypes.size() > 0) {
            basicTypes = new HashSet<>();
            compositeTypes = new HashSet<>();

            for (String stringEndpointType : endpointTypes) {
                try {
                    CompositeEndpointType compositeType = CompositeEndpointType.fromString(stringEndpointType);
                    if (compositeType.getSubType() == null) {
                        basicTypes.add(compositeType.getType());
                    } else {
                        compositeTypes.add(compositeType);
                    }
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Unknown endpoint type: [" + stringEndpointType + "]", e);
                }
            }
        }

        String orgId = getOrgId(securityContext);
        List<Event> events = eventRepository.getEvents(orgId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, basicTypes, compositeTypes, invocationResults, includeActions, notificationStatusSet, query);

        if (events.isEmpty()) {
            Meta meta = new Meta();
            meta.setCount(0L);

            Map<String, String> links = PageLinksBuilder.build(uriInfo.getPath(), 0, query);

            Page<EventLogEntry> page = new Page<>();
            page.setData(new ArrayList<>());
            page.setMeta(meta);
            page.setLinks(links);
            return page;
        }

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
                    action.setStatus(fromNotificationStatus(historyEntry.getStatus()));
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
        Long count = eventRepository.count(orgId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, basicTypes, compositeTypes, invocationResults, notificationStatusSet);

        Meta meta = new Meta();
        meta.setCount(count);

        Map<String, String> links = PageLinksBuilder.build(uriInfo.getPath(), count, query);

        Page<EventLogEntry> page = new Page<>();
        page.setData(eventLogEntries);
        page.setMeta(meta);
        page.setLinks(links);
        return page;
    }

    static EventLogEntryActionStatus fromNotificationStatus(NotificationStatus status) {
        switch (status) {
            case SENT:
                return EventLogEntryActionStatus.SENT;
            case SUCCESS:
                return EventLogEntryActionStatus.SUCCESS;
            case PROCESSING:
                return EventLogEntryActionStatus.PROCESSING;
            case FAILED_EXTERNAL:
            case FAILED_INTERNAL:
                return EventLogEntryActionStatus.FAILED;
            default:
                Log.warnf("Uncovered status found:[%s]. This is a bug", status);
                return EventLogEntryActionStatus.UNKNOWN;
        }
    }

    static Set<NotificationStatus> toNotificationStatus(Set<EventLogEntryActionStatus> statusSet) {
        if (statusSet.stream().anyMatch(Objects::isNull)) {
            throw new BadRequestException("Unable to filter by 'null' status");
        }

        Set<NotificationStatus> notificationStatusSet = new HashSet<>();
        statusSet.forEach(status -> {
            switch (status) {
                case SUCCESS:
                    notificationStatusSet.add(NotificationStatus.SUCCESS);
                    break;
                case SENT:
                    notificationStatusSet.add(NotificationStatus.SENT);
                    break;
                case FAILED:
                    notificationStatusSet.add(NotificationStatus.FAILED_EXTERNAL);
                    notificationStatusSet.add(NotificationStatus.FAILED_INTERNAL);
                    break;
                case PROCESSING:
                    notificationStatusSet.add(NotificationStatus.PROCESSING);
                    break;
                case UNKNOWN:
                    throw new BadRequestException("Unable to filter by 'Unknown' status");
                default:
                    throw new BadRequestException(String.format("Unsupported filter value: [%s]", status));
            }
        });

        return notificationStatusSet;
    }
}
