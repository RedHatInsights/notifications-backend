package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateRequest;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(API_INTERNAL + "/template-engine")
@RegisterRestClient(configKey = "template-engine")
@RegisterProvider(BadRequestExceptionMapper.class)
public interface TemplateEngineClient {

    @PUT
    @Path("/render")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    Uni<Response> render(@NotNull @Valid RenderEmailTemplateRequest renderEmailTemplateRequest);
}
