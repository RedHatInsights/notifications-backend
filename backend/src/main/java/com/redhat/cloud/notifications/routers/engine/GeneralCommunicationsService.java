package com.redhat.cloud.notifications.routers.engine;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.routers.general.communication.SendGeneralCommunicationResponse;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "internal-engine")
public interface GeneralCommunicationsService {
    /**
     * Triggers a general communication via email to Red Hat customers.
     * @return the response body received from the engine.
     */
    @Path(Constants.API_INTERNAL + "/general-communications/send")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Retry(maxRetries = 3)
    SendGeneralCommunicationResponse sendGeneralCommunication();
}
