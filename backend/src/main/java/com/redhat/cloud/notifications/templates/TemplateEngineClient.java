package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(API_INTERNAL + "/template-engine")
@RegisterRestClient(configKey = "internal-engine")
@RegisterProvider(BadRequestExceptionMapper.class)
public interface TemplateEngineClient {

    @PUT
    @Path("/render")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(hidden = true)
    Response render(@NotNull @Valid RenderEmailTemplateRequest renderEmailTemplateRequest);

    @DELETE
    @Path("/migrate")
    @Operation(hidden = true)
    void deleteAllTemplates();

    @PUT
    @Path("/migrate")
    @Operation(hidden = true)
    void migrate();
}
