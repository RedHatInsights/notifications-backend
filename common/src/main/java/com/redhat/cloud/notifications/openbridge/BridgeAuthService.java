package com.redhat.cloud.notifications.openbridge;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.Map;

/**
 * Talk to the IDM server of openbridge to get a bearer token
 */
@RegisterRestClient(configKey = "kc")
@Path("/auth/realms/redhat-external/protocol/openid-connect/token")
@RegisterProvider(RhoseResponseExceptionMapper.class)
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
