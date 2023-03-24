package com.redhat.cloud.notifications.processors.teams;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import static com.redhat.cloud.notifications.processors.teams.TeamsRouteBuilder.REST_PATH;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@RegisterRestClient(configKey = "internal-teams")
@Path(REST_PATH)
public interface InternalTemporaryTeamsService {

    @POST
    @Consumes(APPLICATION_JSON)
    @Retry
    void send(@NotNull TeamsNotification notification);
}
