package com.redhat.cloud.notifications.routers;

import io.quarkus.vertx.web.RouteFilter;
import io.vertx.ext.web.RoutingContext;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redirect routes with only the major version
 * to major.minor ones.
 */
@SuppressWarnings("unused")
public class RouteRedirector {

    Pattern p = Pattern.compile("/api/(integrations|notifications)/v1/(.*)");

    Logger log = Logger.getLogger(this.getClass().getName());

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
        Matcher m = p.matcher(uri);
        if (m.matches()) {
            String newTarget = "/api/" + m.group(1) + "/v1.0/" + m.group(2);
            if (log.isLoggable(Level.FINER)) {
                log.finer("Rerouting to :" + newTarget);
            }

            rc.reroute(newTarget);
            return;
        }
        rc.next();
    }
}
