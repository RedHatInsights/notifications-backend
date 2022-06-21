package com.redhat.cloud.notifications.openbridge;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;

/**
 * Talk to the IDM server of openbridge to get a bearer token
 */
@RegisterRestClient(configKey = "kc")
@Path("/auth/realms/redhat-external/protocol/openid-connect/token")
public interface BridgeAuthService {

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Object> getTokenStructWithClientPassword(String body,
                                                         @HeaderParam("Authorization") String authHeader);

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Object> getTokenStructWithClientCredentials(String body);
}
