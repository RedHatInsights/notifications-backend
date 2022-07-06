package com.redhat.cloud.notifications.openbridge;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;

/**
 * Talk to the OpenBridge manager API to set up processors etc
 */
@Path("/api/smartevents_mgmt/v1/bridges")
@Produces(MediaType.APPLICATION_JSON)
@RegisterRestClient(configKey = "ob")
public interface BridgeApiService {

    @GET
    @Path("/")
    Map<String, Object> getBridges(
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/{bridgeId}")
    Map<String, String> getBridgeById(@PathParam("bridgeId") String bridgeId,
                                      @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/{bridgeId}/processors")
    Map<String, Object> getProcessors(@PathParam("bridgeId") String bridgeId,
                                      @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{bridgeId}/processors")
    Processor addProcessor(@PathParam("bridgeId") String bridgeId,
                                    @HeaderParam("Authorization") String bearerToken,
                                    Processor definition
    );

    @DELETE
    @Path("/{bridgeId}/processors/{processorId}")
    void deleteProcessor(@PathParam("bridgeId") String bridgeId,
                    @PathParam("processorId") String processorId,
                    @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/{bridgeId}/processors/{processorId}")
    Processor getProcessorById(@PathParam("bridgeId") String bridgeId,
                    @PathParam("processorId") String processorId,
                    @HeaderParam("Authorization") String bearerToken
    );

    @PUT
    @Path("/{bridgeId}/processors/{processorId}")
    Processor updateProcessor(@PathParam("bridgeId") String bridgeId,
                    @PathParam("processorId") String processorId,
                    @HeaderParam("Authorization") String bearerToken,
                    Processor processor
    );


}
