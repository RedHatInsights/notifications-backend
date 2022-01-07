package com.redhat.cloud.notifications.recipients.itservice;

import com.redhat.cloud.notifications.recipients.rbac.RbacUser;
import com.redhat.cloud.notifications.routers.models.Page;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@RegisterRestClient(configKey = "it-s2s")
public interface ITUserService {

    @POST
    @Path("/findUsers")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Page<RbacUser>> getUsers(String string);
}
