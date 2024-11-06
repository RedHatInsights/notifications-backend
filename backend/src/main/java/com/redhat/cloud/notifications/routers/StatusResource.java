package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.models.CurrentStatus;
import com.redhat.cloud.notifications.models.Status;
import com.redhat.cloud.notifications.oapi.OApiFilter;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_2_0;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class StatusResource {

    @Path(API_NOTIFICATIONS_V_1_0 + "/status")
    public static class V1 extends StatusResource {
    }

    @Path(API_NOTIFICATIONS_V_2_0 + "/status")
    public static class V2 extends StatusResource {
    }

    @GET
    @Produces(APPLICATION_JSON)
    @Tag(name = OApiFilter.PRIVATE)
    public CurrentStatus getCurrentStatus() {
        CurrentStatus cs = new CurrentStatus();
        cs.setStatus(Status.UP);
        return cs;
    }
}
