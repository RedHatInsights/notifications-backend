package com.redhat.cloud.notifications.templates;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;

@Path(API_INTERNAL + "/template-engine")
@RegisterRestClient(configKey = "internal-engine")
@RegisterProvider(BadRequestExceptionMapper.class)
public interface TemplateEngineClient {

    @DELETE
    @Path("/migrate")
    @Operation(hidden = true)
    void deleteAllTemplates();

    @PUT
    @Path("/migrate")
    @Operation(hidden = true)
    void migrate();
}
