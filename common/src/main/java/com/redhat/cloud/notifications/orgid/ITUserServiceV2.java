package com.redhat.cloud.notifications.orgid;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

// TODO NOTIF-744 Remove this as soon as all onboarded apps include the org_id field in their Kafka messages.
@RegisterRestClient(configKey = "it-s2s")
public interface ITUserServiceV2 {

    @POST
    @Path("/v2/findAccount")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    OrgIdResponse getOrgId(OrgIdRequest request);
}
