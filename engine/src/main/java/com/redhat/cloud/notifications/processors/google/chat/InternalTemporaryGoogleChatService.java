package com.redhat.cloud.notifications.processors.google.chat;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;


import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@RegisterRestClient(configKey = "internal-google-chat")
@Path(GoogleChatRouteBuilder.REST_PATH)
public interface InternalTemporaryGoogleChatService {

    @POST
    @Consumes(APPLICATION_JSON)
    @Retry
    void send(@NotNull GoogleChatNotification notification);
}
