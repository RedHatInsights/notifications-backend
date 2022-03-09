package com.redhat.cloud.notifications.openbridge;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;

/**
 * Deliver events to the OpenBridge ingress
 */
@Path("/events")
public interface BridgeEventService {

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    void sendEvent(Map<String, Object> payload,
                      @HeaderParam("Authorization") String bearerToken
    );

}
