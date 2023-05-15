package com.redhat.cloud.notifications.processors.teams;

import com.redhat.cloud.notifications.processors.camel.InternalCamelTemporaryService;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import javax.ws.rs.Path;

import static com.redhat.cloud.notifications.processors.teams.TeamsRouteBuilder.REST_PATH;

@RegisterRestClient(configKey = "internal-teams")
@Path(REST_PATH)
public interface InternalTemporaryTeamsService extends InternalCamelTemporaryService {

}
