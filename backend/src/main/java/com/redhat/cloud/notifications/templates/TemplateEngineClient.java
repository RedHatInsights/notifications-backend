package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateRequest;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestQuery;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
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

    @GET
    @Path("/subscription_type_supported")
    @Produces(APPLICATION_JSON)
    Boolean isSubscriptionTypeSupported(@NotNull @RestQuery String bundleName, @NotNull @RestQuery String applicationName, @NotNull @RestQuery EmailSubscriptionType subscriptionType);

    @PUT
    @Path("/render")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    Response render(@NotNull @Valid RenderEmailTemplateRequest renderEmailTemplateRequest);
}
