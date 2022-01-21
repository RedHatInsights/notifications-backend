package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateRequest;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateResponse;
import com.redhat.cloud.notifications.utils.ActionParser;
import io.smallrye.mutiny.Uni;
import org.jboss.resteasy.reactive.RestQuery;

import javax.inject.Inject;
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

// TODO: Move this class to notifications-engine.

@Path(API_INTERNAL + "/template-engine")
public class TemplateEngineResource {

    @Inject
    ActionParser actionParser;

    @Inject
    EmailTemplateService emailTemplateService;

    @Inject
    EmailTemplateFactory emailTemplateFactory;

    @GET
    @Path("/subscription_type_supported")
    @Produces(APPLICATION_JSON)
    public Uni<Boolean> isSubscriptionTypeSupported(@NotNull @RestQuery String bundleName, @NotNull @RestQuery String applicationName, @NotNull @RestQuery EmailSubscriptionType subscriptionType) {
        return Uni.createFrom().item(() -> emailTemplateFactory.get(bundleName, applicationName).isEmailSubscriptionSupported(subscriptionType));
    }

    @PUT
    @Path("/render")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Uni<Response> render(@NotNull @Valid RenderEmailTemplateRequest renderEmailTemplateRequest) {
        User user = createInternalUser();

        String payload = renderEmailTemplateRequest.getPayload();
        return actionParser.fromJsonString(payload)
                .onItem().transformToUni(action -> Uni.combine().all().unis(
                                emailTemplateService
                                        .compileTemplate(renderEmailTemplateRequest.getSubjectTemplate(), "subject")
                                        .onItem().transformToUni(templateInstance -> emailTemplateService.renderTemplate(
                                                user,
                                                action,
                                                templateInstance
                                        )),
                                emailTemplateService
                                        .compileTemplate(renderEmailTemplateRequest.getBodyTemplate(), "body")
                                        .onItem().transformToUni(templateInstance -> emailTemplateService.renderTemplate(
                                                user,
                                                action,
                                                templateInstance
                                        ))
                        ).asTuple()
                ).onItem().transform(titleAndBody -> Response.ok(new RenderEmailTemplateResponse.Success(titleAndBody.getItem1(), titleAndBody.getItem2())).build())
                .onFailure().recoverWithItem(throwable -> Response.status(Response.Status.BAD_REQUEST).entity(new RenderEmailTemplateResponse.Error(throwable.getMessage())).build());
    }

    private User createInternalUser() {
        User user = new User();
        user.setUsername("jdoe");
        user.setEmail("jdoe@jdoe.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setActive(true);
        user.setAdmin(false);
        return user;
    }
}
