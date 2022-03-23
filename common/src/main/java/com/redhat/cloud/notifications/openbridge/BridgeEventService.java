package com.redhat.cloud.notifications.openbridge;

import io.vertx.core.json.JsonObject;

import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;

/**
 * Deliver events to the OpenBridge ingress
 */
@Path("/events")
public interface BridgeEventService {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    void sendEvent(JsonObject payload,
                   @HeaderParam("Authorization") String bearerToken
    );

}
