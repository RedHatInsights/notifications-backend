package com.redhat.cloud.notifications.openbridge;

import io.vertx.core.json.JsonObject;

import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

/**
 * Deliver events to the OpenBridge ingress.
 * This interface is used to programmatically create
 * a rest-client, as the exact api location is only
 * known at runtime.
 */
@Path("/")
public interface BridgeEventService {

    @POST
    @Consumes("application/cloudevents+json")
    void sendEvent(JsonObject payload,
                   @HeaderParam("Authorization") String bearerToken
    );

}
