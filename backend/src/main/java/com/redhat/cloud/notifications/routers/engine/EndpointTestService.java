package com.redhat.cloud.notifications.routers.engine;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.routers.endpoints.InternalEndpointTestRequest;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "internal-engine")
public interface EndpointTestService {

    /**
     * Sends a request to the engine to test the provided endpoint. This
     * happens when the client wants to test their integration with a test
     * event.
     * @param internalEndpointTestRequest the payload of the request.
     */
    @Path(Constants.API_INTERNAL + "/endpoints/test")
    @POST
    @Retry(maxRetries = 3)
    void testEndpoint(InternalEndpointTestRequest internalEndpointTestRequest);
}
