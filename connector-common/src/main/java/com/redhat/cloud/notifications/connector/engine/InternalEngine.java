package com.redhat.cloud.notifications.connector.engine;

import com.redhat.cloud.notifications.connector.payload.PayloadDetails;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestPath;

@Path("/internal")
@RegisterRestClient(configKey = "engine")
public interface InternalEngine {
    /**
     * Fetches the payload from the engine.
     * @param payloadId the identifier of the payload to fetch.
     * @return the payload contents.
     */
    @GET
    @Path("/payloads/{payloadId}")
    @Produces(MediaType.APPLICATION_JSON)
    PayloadDetails getPayloadDetails(@RestPath String payloadId);
}
