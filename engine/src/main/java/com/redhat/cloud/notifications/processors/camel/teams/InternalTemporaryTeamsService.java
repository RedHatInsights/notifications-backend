package com.redhat.cloud.notifications.processors.camel.teams;

import com.redhat.cloud.notifications.processors.camel.InternalCamelTemporaryService;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import javax.ws.rs.Path;

@RegisterRestClient(configKey = "internal-teams")
@Path(TeamsRouteBuilder.REST_PATH)
public interface InternalTemporaryTeamsService extends InternalCamelTemporaryService {

}
