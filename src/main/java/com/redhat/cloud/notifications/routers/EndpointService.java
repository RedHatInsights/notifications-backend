package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.models.Endpoint;
import io.smallrye.mutiny.Multi;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/endpoints")
@ApplicationScoped
public class EndpointService {

//    @GET
//    // TODO Needs a filter or preprocessor to get x-rh-identity parsed
//    public Multi<Endpoint> getEndpoints() {
//
//    }
}
