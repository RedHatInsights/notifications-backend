package com.redhat.cloud.notifications.routers;

import io.quarkus.vertx.web.RouteFilter;
import io.vertx.ext.web.RoutingContext;
import org.jboss.logging.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redirect routes with only the major version
 * to major.minor ones.
 */
@SuppressWarnings("unused")
public class RouteRedirector {

    // Prevents the injection of characters that would break the log file pattern and lead to log forging or log poisoning.
    private static final Pattern ANTI_INJECTION_PATTERN = Pattern.compile("[\n|\r|\t]");

    Pattern p = Pattern.compile("/api/(integrations|notifications)/v1/(.*)");

    private static final Logger log = Logger.getLogger(RouteRedirector.class);

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
        if (log.isTraceEnabled()) {
            String sanitizedUri = ANTI_INJECTION_PATTERN.matcher(uri).replaceAll("");
            log.tracef("Incoming uri: %s", sanitizedUri);
        }
        Matcher m = p.matcher(uri);
        if (m.matches()) {
            String newTarget = "/api/" + m.group(1) + "/v1.0/" + m.group(2);
            log.tracef("Rerouting to: %s", newTarget);

            rc.reroute(newTarget);
            return;
        }
        rc.next();
    }
}
