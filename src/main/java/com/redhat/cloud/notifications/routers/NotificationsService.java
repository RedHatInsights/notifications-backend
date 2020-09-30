package com.redhat.cloud.notifications.routers;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

@Path("/api/notifications/v1.0")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
// TODO Needs documentation annotations
public class NotificationsService {


    @Path("bla")
    @GET
    @RolesAllowed("read")
    public String getNotifications(@Context SecurityContext sec, @Context UriInfo uriInfo) {
        return "Hello World";
    }
}
