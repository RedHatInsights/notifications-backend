package com.redhat.cloud.notifications.events.orgid;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.List;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

// TODO NOTIF-744 Remove this as soon as all onboarded apps include the org_id field in their Kafka messages.
@RegisterRestClient(configKey = "bop")
public interface Bop {

    @POST
    @Path("/v2/accountMapping/orgIds")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    Map<String, String> translateAccountIdsToOrgIds(
            @HeaderParam("x-rh-apitoken") String apiToken,
            @HeaderParam("x-rh-clientid") String clientId,
            @HeaderParam("x-rh-insights-env") String insightsEnv,
            List<String> accountIds
    );
}
