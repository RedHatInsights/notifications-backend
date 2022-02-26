package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.StatusResources;
import com.redhat.cloud.notifications.models.CurrentStatus;
import com.redhat.cloud.notifications.oapi.OApiFilter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_1_0;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(API_NOTIFICATIONS_V_1_0 + "/status")
public class StatusService {

    @Inject
    StatusResources statusResources;

    @GET
    @Produces(APPLICATION_JSON)
    @Tag(name = OApiFilter.PRIVATE)
    public CurrentStatus getCurrentStatus() {
        return statusResources.getCurrentStatus();
    }
}
