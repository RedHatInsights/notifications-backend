package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateRequest;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateResponse;
import com.redhat.cloud.notifications.utils.ActionParser;
import io.quarkus.qute.TemplateException;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(API_INTERNAL + "/template-engine")
public class TemplateEngineResource {

    @Inject
    ActionParser actionParser;

    @Inject
    TemplateService templateService;

    @PUT
    @Path("/render")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response render(@NotNull @Valid RenderEmailTemplateRequest renderEmailTemplateRequest) {

        String payload = renderEmailTemplateRequest.getPayload();
        try {
            Action action = actionParser.fromJsonString(payload);

            String[] templateContent = renderEmailTemplateRequest.getTemplate();
            String[] renderedTemplate = new String[templateContent.length];

            for (int i = 0; i < templateContent.length; i++) {
                TemplateInstance template = templateService.compileTemplate(templateContent[i], String.format("rendered-template-%d", i));
                renderedTemplate[i] = templateService.renderTemplate(action, template, null);
            }

            return Response.ok(new RenderEmailTemplateResponse.Success(renderedTemplate)).build();
        } catch (Exception e) {
            final String errorMessage;

            // For some reason the error message is in the nested exception of
            // the thrown exception.
            if (e.getCause() instanceof TemplateException templateException) {
                errorMessage = templateException.getMessage();
            } else {
                errorMessage = e.getMessage();
            }

            return Response.status(Response.Status.BAD_REQUEST).entity(new RenderEmailTemplateResponse.Error(errorMessage)).build();
        }
    }
}
