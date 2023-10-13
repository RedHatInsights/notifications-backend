package com.redhat.cloud.notifications.recipientresolver.resolver.itservice;

import com.redhat.cloud.notifications.recipientresolver.resolver.itservice.pojo.request.ITUserRequest;
import com.redhat.cloud.notifications.recipientresolver.resolver.itservice.pojo.response.ITUserResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.util.List;

@RegisterRestClient(configKey = "it-s2s")
public interface ITUserService {

    @POST
    @Path("/v2/findUsers")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    List<ITUserResponse> getUsers(ITUserRequest itUserRequest);
}
