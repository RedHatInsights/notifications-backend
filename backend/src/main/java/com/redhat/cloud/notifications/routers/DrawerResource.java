package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.repositories.DrawerNotificationRepository;
import com.redhat.cloud.notifications.models.DrawerEntryPayload;
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
import org.jboss.resteasy.reactive.RestQuery;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getUsername;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class DrawerResource {

    @Inject
    DrawerNotificationRepository drawerRepository;

    @Inject
    BackendConfig backendConfig;

    @Path(API_NOTIFICATIONS_V_1_0 + "/notifications/drawer")
    public static class V1 extends DrawerResource {

    }

    @GET
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve drawer notifications entries.", description =
            "Allowed `sort_by` fields are `bundleIds`, `applicationIds`, `eventTypeIds`, `startTime`, `endTime` and `read`. The ordering can be optionally specified by appending `:asc` or `:desc` to the field, e.g. `bundle:desc`. Defaults to `desc` for the `created` field and to `asc` for all other fields."
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
        if (backendConfig.isDrawerEnabled()) {
            count = drawerRepository.count(orgId, username, bundleIds, appIds, eventTypeIds, startDate, endDate, readStatus);
            if (count > 0) {
                drawerEntries = drawerRepository.getNotifications(orgId, username, bundleIds, appIds, eventTypeIds, startDate, endDate, readStatus, query);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        Log.infof("Drawer request duration %s for orgId: %s, userId: %s",
            ChronoUnit.MILLIS.between(start, now),
            orgId,
            username);
        Meta meta = new Meta();
        meta.setCount(count);

        Map<String, String> links = PageLinksBuilder.build(uriInfo.getPath(), count, query);

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
}
