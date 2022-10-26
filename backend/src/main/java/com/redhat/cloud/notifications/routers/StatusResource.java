package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.repositories.StatusRepository;
import com.redhat.cloud.notifications.models.CurrentStatus;
import com.redhat.cloud.notifications.oapi.OApiFilter;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_1_0;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(API_NOTIFICATIONS_V_1_0 + "/status")
public class StatusResource {

    @Inject
    StatusRepository statusRepository;

    @GET
    @Produces(APPLICATION_JSON)
    @Tag(name = OApiFilter.PRIVATE)
    public CurrentStatus getCurrentStatus() {
        return statusRepository.getCurrentStatus();
    }
}
