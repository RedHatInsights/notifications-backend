package com.redhat.cloud.notifications.oapi;

import io.quarkus.vertx.web.RouteFilter;
import io.vertx.ext.web.RoutingContext;

/**
 * Change the accept header if needed for Openapi requests when
 * openapi.json is requested.
 */
public class OASAcceptHeaderMangler {

    /*
     * CPOL-107
     * Default return format for openapi is .yml
     * If the user requests 'openapi.json', the user assumes
     * that a JSON format is returned. Unfortunately does Quarkus not
     * honor the '.json' suffix but either requires a correct Accept
     * header or the use of a query parameter.
     *
     * We now look at the path and if it ends in .json, replace the
     * existing Accept header with one that requests Json format.
     */
    @RouteFilter(401)
    void oasAcceptHeaderMangler(RoutingContext rc) {
        if (rc.normalisedPath().endsWith("openapi.json")) {
            rc.request().headers().remove("Accept");
            rc.request().headers().add("Accept", "application/json");
        }
        rc.next();
    }
}
