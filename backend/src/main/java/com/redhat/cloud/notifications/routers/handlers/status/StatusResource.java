package com.redhat.cloud.notifications.routers.handlers.status;

import com.redhat.cloud.notifications.oapi.OApiFilter;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class StatusResource {

    static final JsonObject STATUS = new JsonObject("{\"status\":\"UP\"}");

    @GET
    @Produces(APPLICATION_JSON)
    @Tag(name = OApiFilter.PRIVATE)
    public JsonObject getCurrentStatus() {
        return STATUS;
    }
}
