package com.redhat.cloud.notifications.routers;

import io.quarkus.vertx.web.RouteFilter;
import io.vertx.ext.web.RoutingContext;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Redirect routes with only the major version
 * to major.minor ones.
 */
@SuppressWarnings("unused")
public class RouteRedirector {

    private static final String API_NOTIFICATIONS_V_1 = "/api/notifications/v1/";
    private static final String API_NOTIFICATIONS_V_1_0 = "/api/notifications/v1.0/";

    Logger log = Logger.getLogger(this.getClass().getSimpleName());

    /**
     * If the requested route is the one with major version only,
     * we rewrite it on the fly.
     * We need to take the URI from the underlying http request, as the
     * normalised path does not contain query parameters.
     *
     * @param rc RoutingContext from vert.x
     */
    @RouteFilter(400)
    void myRedirector(RoutingContext rc) {
        String uri = rc.request().uri();
        if (log.isLoggable(Level.FINER)) {
            log.finer("Incoming uri: " + uri);
        }
        if (uri.startsWith(API_NOTIFICATIONS_V_1)) {
            String remain = uri.substring(API_NOTIFICATIONS_V_1.length());
            if (log.isLoggable(Level.FINER)) {
                log.finer("Rerouting to :" + API_NOTIFICATIONS_V_1_0 + remain);
            }

            rc.reroute(API_NOTIFICATIONS_V_1_0 + remain);
            return;
        }
        rc.next();
    }
}
