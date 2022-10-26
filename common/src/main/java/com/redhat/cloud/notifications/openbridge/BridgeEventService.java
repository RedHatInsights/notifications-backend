package com.redhat.cloud.notifications.openbridge;

import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

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
