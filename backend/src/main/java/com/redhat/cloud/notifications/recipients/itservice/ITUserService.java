package com.redhat.cloud.notifications.recipients.itservice;

import com.redhat.cloud.notifications.recipients.itservice.pojo.request.ITUserRequest;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.ITUserResponse;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import java.util.List;

@RegisterRestClient(configKey = "it-s2s")
public interface ITUserService {

    @POST
    @Path("/findUsers")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<List<ITUserResponse>> getUsers(ITUserRequest string);
}
