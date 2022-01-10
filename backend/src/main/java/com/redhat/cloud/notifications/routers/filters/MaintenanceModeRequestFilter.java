package com.redhat.cloud.notifications.routers.filters;

import com.redhat.cloud.notifications.db.StatusResources;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;

import java.util.List;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.models.Status.MAINTENANCE;
import static javax.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;

@ApplicationScoped
public class MaintenanceModeRequestFilter {

    private static final Logger LOGGER = Logger.getLogger(MaintenanceModeRequestFilter.class);

    // This list contains all request paths that should never be affected by the maintenance mode.
    private static final List<String> NO_MAINTENANCE_REQUEST_PATHS = List.of(
            API_INTERNAL,
            "/health",
            "/metrics",
            API_NOTIFICATIONS_V_1_0 + "/status"
    );

    private static final Response MAINTENANCE_IN_PROGRESS = Response.status(SERVICE_UNAVAILABLE).entity("Maintenance in progress").build();

    @Inject
    StatusResources statusResources;

    @ServerRequestFilter
    public Uni<Response> filter(ContainerRequestContext requestContext) {
        String requestPath = requestContext.getUriInfo().getRequestUri().getPath();
        LOGGER.tracef("Filtering request to %s", requestPath);

        // First, we check if the request path should be affected by the maintenance mode.
        for (int i = 0; i < NO_MAINTENANCE_REQUEST_PATHS.size(); i++) {
            if (requestPath.startsWith(NO_MAINTENANCE_REQUEST_PATHS.get(i))) {
                LOGGER.trace("Request path shouldn't be affected by the maintenance mode, database check will be skipped");
                // This filter work is done. The request will be processed normally.
                return null;
            }
        }

        /*
         * If this point is reached, the current request path can be affected by the maintenance mode.
         * Let's check if maintenance is on in the database.
         */
        return isMaintenance()
                .onItem().transform(isMaintenance -> {
                    if (isMaintenance) {
                        LOGGER.trace("Maintenance mode is enabled in the database, aborting request and returning HTTP status 503");
                        return MAINTENANCE_IN_PROGRESS;
                    } else {
                        // This filter work is done. The request will be processed normally.
                        return null;
                    }
                });
    }

    @CacheResult(cacheName = "maintenance")
    public Uni<Boolean> isMaintenance() {
        return statusResources.getCurrentStatus()
                .onItem().transform(currentStatus -> currentStatus.status == MAINTENANCE);
    }
}
