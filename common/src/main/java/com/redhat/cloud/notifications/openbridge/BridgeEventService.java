package com.redhat.cloud.notifications.openbridge;

import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import java.util.Map;

/**
 * Deliver events to the OpenBridge ingress
 */
@Path("/events")
public interface BridgeEventService {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    void sendEvent(Map<String, Object> payload,
                      @HeaderParam("Authorization") String bearerToken
    );

}
