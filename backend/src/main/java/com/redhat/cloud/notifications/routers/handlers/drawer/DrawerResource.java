package com.redhat.cloud.notifications.routers.handlers.drawer;

import com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.repositories.DrawerNotificationRepository;
import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.models.DrawerEntryPayload;
import com.redhat.cloud.notifications.routers.handlers.event.EventAuthorizationCriterion;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import com.redhat.cloud.notifications.routers.models.PageLinksBuilder;
import com.redhat.cloud.notifications.routers.models.UpdateNotificationDrawerStatus;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.jboss.resteasy.reactive.RestQuery;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.db.Query.DEFAULT_RESULTS_PER_PAGE;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getUsername;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class DrawerResource {

    @Path(API_NOTIFICATIONS_V_1_0 + "/notifications/drawer")
    public static class V1 extends DrawerResource {
    }

    @Inject
    DrawerNotificationRepository drawerRepository;

    @Inject
    BackendConfig backendConfig;

    @Inject
    EventRepository eventRepository;

    @Inject
    KesselInventoryAuthorization kesselInventoryAuthorization;

    @GET
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve drawer notifications entries.", description =
            "Retrieve paginated drawer notifications with optional filtering and sorting. " +
            "Available filters: `bundleIds`, `appIds`, `eventTypeIds`, `startDate`, `endDate`, `readStatus`. " +
            "Allowed `sort_by` fields: `bundle`, `application`, `event`, `created`. " +
            "Sorting can be specified by appending `:asc` or `:desc` to the field, e.g. `bundle:desc`. " +
            "Defaults to `created:desc`."
    )
    @Parameter(
        name = "limit",
        in = ParameterIn.QUERY,
        description = "Number of items per page, if not specified " + DEFAULT_RESULTS_PER_PAGE + " is used",
        schema = @Schema(type = SchemaType.INTEGER, defaultValue = DEFAULT_RESULTS_PER_PAGE + "")
    )
    public Page<DrawerEntryPayload> getDrawerEntries(@Context SecurityContext securityContext, @Context UriInfo uriInfo,
                                         @RestQuery Set<UUID> bundleIds, @RestQuery Set<UUID> appIds,
                                         @RestQuery Set<UUID> eventTypeIds, @RestQuery LocalDateTime startDate, @RestQuery LocalDateTime endDate,
                                         @RestQuery Boolean readStatus,
                                         @BeanParam @Valid Query query) {

        String orgId = getOrgId(securityContext);
        String username = getUsername(securityContext);
        LocalDateTime start = LocalDateTime.now();
        List<DrawerEntryPayload> drawerEntries = new ArrayList<>();
        Long count = 0L;
        if (backendConfig.isDrawerEnabled(orgId)) {

            List<UUID> excludedEventIds = computeExcludedEventIds(
                securityContext, orgId, username, startDate, endDate
            );

            count = drawerRepository.count(
                orgId, username, bundleIds, appIds, eventTypeIds,
                startDate, endDate, readStatus, excludedEventIds
            );
            if (count > 0) {
                drawerEntries = drawerRepository.getNotifications(
                    orgId, username, bundleIds, appIds, eventTypeIds,
                    startDate, endDate, readStatus, query, excludedEventIds
                );
            }
        }
        LocalDateTime now = LocalDateTime.now();
        Log.infof("Drawer request duration %s for orgId: %s, userId: %s",
            ChronoUnit.MILLIS.between(start, now),
            orgId,
            username);
        Meta meta = new Meta();
        meta.setCount(count);

        Map<String, String> links = PageLinksBuilder.build(uriInfo, count, query);

        Page<DrawerEntryPayload> page = new Page<>();
        page.setData(drawerEntries);
        page.setMeta(meta);
        page.setLinks(links);
        return page;
    }

    @PUT
    @Path("/read")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Update drawer notifications status.", description =
        "Update drawer notifications status."
    )
    public Integer updateNotificationReadStatus(@Context SecurityContext securityContext, UpdateNotificationDrawerStatus drawerStatus) {
        String orgId = getOrgId(securityContext);
        String username = getUsername(securityContext);
        return drawerRepository.updateReadStatus(orgId, username, drawerStatus.getNotificationIds(), drawerStatus.getReadStatus());
    }

    /**
     * Compute excluded event IDs based on authorization checks.
     */
    private List<UUID> computeExcludedEventIds(SecurityContext securityContext,
                                               String orgId,
                                               String username,
                                               LocalDateTime startDate,
                                               LocalDateTime endDate) {
        List<UUID> uuidToExclude = new ArrayList<>();

        if (!backendConfig.isKesselChecksOnEventLogEnabled(orgId)) {
            return uuidToExclude;
        }

        boolean useNormalized = backendConfig.isNormalizedQueriesEnabled(orgId);
        Set<UUID> subscribedEventTypes = drawerRepository.getSubscribedEventTypes(orgId, username);

        if (subscribedEventTypes.isEmpty()) {
            return uuidToExclude;
        }

        Log.info("Check for drawer events with authorization criterion");
        List<EventAuthorizationCriterion> listEventsAuthCriterion =
            eventRepository.getDrawerEventsWithCriterion(
                orgId, useNormalized, subscribedEventTypes, startDate, endDate
            );

        Map<Integer, Boolean> criterionResultCache = new HashMap<>();
        for (EventAuthorizationCriterion eventAuthorizationCriterion : listEventsAuthCriterion) {
            int criterionHashCode = eventAuthorizationCriterion.authorizationCriterion().hashCode();
            if (!criterionResultCache.containsKey(criterionHashCode)) {
                criterionResultCache.put(criterionHashCode,
                    kesselInventoryAuthorization.hasPermissionOnResource(
                        securityContext,
                        eventAuthorizationCriterion.authorizationCriterion()
                    ));
            }
            if (!criterionResultCache.get(criterionHashCode)) {
                Log.infof("%s is not visible for current user", eventAuthorizationCriterion.id());
                uuidToExclude.add(eventAuthorizationCriterion.id());
            }
        }

        return uuidToExclude;
    }
}
