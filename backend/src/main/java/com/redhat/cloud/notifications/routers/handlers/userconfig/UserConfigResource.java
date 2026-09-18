package com.redhat.cloud.notifications.routers.handlers.userconfig;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.oapi.OApiFilter;
import com.redhat.cloud.notifications.routers.models.SettingsValuesByEventType;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

@Path(Constants.API_NOTIFICATIONS_V_1_0 + "/user-config")
public class UserConfigResource extends UserConfigResourceCommon {

    @POST
    @Path("/notification-event-type-preference")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Tag(name = OApiFilter.PRIVATE)
    @Transactional
    public Response saveSettingsByEventType(@Context SecurityContext sec, @NotNull @Valid SettingsValuesByEventType userSettings) {
        return doSaveSettingsByEventType(sec, userSettings);
    }

    @GET
    @Path("/notification-event-type-preference")
    @Produces(APPLICATION_JSON)
    @Tag(name = OApiFilter.PRIVATE)
    public Response getSettingsSchemaByEventType(@Context SecurityContext sec) {
        return super.getSettingsSchemaByEventType(sec);
    }

    @GET
    @Path("/notification-event-type-preference/{bundleName}/{applicationName}")
    @Produces(APPLICATION_JSON)
    @Tag(name = OApiFilter.PRIVATE)
    public Response getPreferencesByEventType(
            @Context SecurityContext sec, @RestPath String bundleName, @RestPath String applicationName) {
        return super.getPreferencesByEventType(sec, bundleName, applicationName);
    }
}
