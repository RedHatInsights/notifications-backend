package com.redhat.cloud.notifications.routers.filters;

import com.redhat.cloud.notifications.config.BackendConfig;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import java.util.List;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_2_0;
import static jakarta.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;

@ApplicationScoped
public class MaintenanceModeRequestFilter {

    // This list contains all request paths that should never be affected by the maintenance mode.
    private static final List<String> NO_MAINTENANCE_REQUEST_PATHS = List.of(
            API_INTERNAL,
            "/health",
            "/metrics",
            API_NOTIFICATIONS_V_1_0 + "/status",
            API_NOTIFICATIONS_V_2_0 + "/status"
    );

    protected static final Response MAINTENANCE_IN_PROGRESS = Response.status(SERVICE_UNAVAILABLE).entity("Maintenance in progress").build();

    @Inject
    BackendConfig backendConfig;

    @ServerRequestFilter
    public Response filter(ContainerRequestContext requestContext) {
        String requestPath = requestContext.getUriInfo().getRequestUri().getPath();
        Log.tracef("Filtering request to %s", requestPath);

        if (!isAffectedByMaintenanceMode(requestPath)) {
            return null;
        }

        /*
         * If this point is reached, the current request path can be affected by the maintenance mode.
         * Let's check if maintenance is on in the database.
         */
        if (isMaintenance(requestContext.getMethod() + "_" + requestPath)) {
            Log.trace("Maintenance mode is enabled in the database, aborting request and returning HTTP status 503");
            return MAINTENANCE_IN_PROGRESS;
        } else {
            // This filter work is done. The request will be processed normally.
            return null;
        }
    }

    boolean isAffectedByMaintenanceMode(String requestPath) {
        // First, we check if the request path should be affected by the maintenance mode.
        for (String noMaintenanceRequestPath : NO_MAINTENANCE_REQUEST_PATHS) {
            if (requestPath.startsWith(noMaintenanceRequestPath)) {
                Log.trace("Request path shouldn't be affected by the maintenance mode, database check will be skipped");
                // This filter work is done. The request will be processed normally.
                return false;
            }
        }
        return true;
    }

    @CacheResult(cacheName = "maintenance")
    public Boolean isMaintenance(String methodAndRequestPath) {
        return backendConfig.isMaintenanceModeEnabled(methodAndRequestPath);
    }
}
