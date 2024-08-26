package com.redhat.cloud.notifications.routers.engine;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;

@RegisterRestClient(configKey = "internal-engine")
public interface ReplayService {

    @Path(API_INTERNAL + "/replay")
    @POST
    void replay();
}
