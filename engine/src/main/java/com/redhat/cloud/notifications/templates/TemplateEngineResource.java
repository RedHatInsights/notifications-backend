package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateRequest;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateResponse;
import com.redhat.cloud.notifications.utils.ActionParser;
import io.quarkus.qute.TemplateInstance;
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

@Path(API_INTERNAL + "/template-engine")
public class TemplateEngineResource {

    @Inject
    ActionParser actionParser;

    @Inject
    TemplateService templateService;

    @Inject
    EmailTemplateFactory emailTemplateFactory;

    @Inject
    TemplateRepository templateRepository;

    @Inject
    FeatureFlipper featureFlipper;

    @GET
    @Path("/subscription_type_supported")
    @Produces(APPLICATION_JSON)
    public Boolean isSubscriptionTypeSupported(@NotNull @RestQuery String bundleName, @NotNull @RestQuery String applicationName, @NotNull @RestQuery EmailSubscriptionType subscriptionType) {
        if (featureFlipper.isUseTemplatesFromDb()) {
            return templateRepository.isEmailSubscriptionSupported(bundleName, applicationName, subscriptionType);
        } else {
            return emailTemplateFactory.get(bundleName, applicationName).isEmailSubscriptionSupported(subscriptionType);
        }
    }

    @PUT
    @Path("/render")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response render(@NotNull @Valid RenderEmailTemplateRequest renderEmailTemplateRequest) {
        User user = createInternalUser();

        String payload = renderEmailTemplateRequest.getPayload();
        try {
            Action action = actionParser.fromJsonString(payload);

            String[] templateContent = renderEmailTemplateRequest.getTemplate();
            String[] renderedTemplate = new String[templateContent.length];

            for (int i = 0; i < templateContent.length; i++) {
                TemplateInstance template = templateService.compileTemplate(templateContent[i], String.format("rendered-template-%d", i));
                renderedTemplate[i] = templateService.renderTemplate(user, action, template);
            }

            return Response.ok(new RenderEmailTemplateResponse.Success(renderedTemplate)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new RenderEmailTemplateResponse.Error(e.getMessage())).build();
        }
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
