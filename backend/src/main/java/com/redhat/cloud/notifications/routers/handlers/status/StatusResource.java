package com.redhat.cloud.notifications.routers.handlers.status;

import com.redhat.cloud.notifications.oapi.OApiFilter;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_1_0;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(API_NOTIFICATIONS_V_1_0 + "/status")
public class StatusResource {

    static final JsonObject STATUS = new JsonObject("{\"status\":\"UP\"}");

    @GET
    @Produces(APPLICATION_JSON)
    @Tag(name = OApiFilter.PRIVATE)
    public JsonObject getCurrentStatus() {
        return STATUS;
    }
}
