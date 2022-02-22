package com.redhat.cloud.notifications.temp.openbridge;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;

/**
 * Talk to the OpenBridge manager API to set up processors etc
 */
@Path("/api/v1/bridges")
@Produces(MediaType.APPLICATION_JSON)
@RegisterRestClient(configKey = "ob")
public interface BridgeApiService {

    @GET
    @Path("/")
    Uni<Map<String, Object>> getBridges(
            @HeaderParam("Authorization") String bearerToken
    );

}
