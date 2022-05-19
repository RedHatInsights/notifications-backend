package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.Template;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateRequest;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateResponse;
import com.redhat.cloud.notifications.templates.TemplateEngineClient;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestPath;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_INTERNAL_USER;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@Path(API_INTERNAL + "/templates")
@RolesAllowed(RBAC_INTERNAL_ADMIN)
public class TemplateResource {

    @Inject
    TemplateRepository templateRepository;

    @Inject
    @RestClient
    TemplateEngineClient templateEngineClient;

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_USER)
    public Template createTemplate(@NotNull @Valid Template template) {
        return templateRepository.createTemplate(template);
    }

    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed(RBAC_INTERNAL_USER)
    public List<Template> getAllTemplates() {
        return templateRepository.findAllTemplates();
    }

    @GET
    @Path("/{templateId}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(RBAC_INTERNAL_USER)
    public Template getTemplate(@RestPath UUID templateId) {
        return templateRepository.findTemplateById(templateId);
    }

    @PUT
    @Path("/{templateId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_USER)
    public Response updateTemplate(@RestPath UUID templateId, Template template) {
        boolean updated = templateRepository.updateTemplate(templateId, template);
        if (updated) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @DELETE
    @Path("/{templateId}")
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_USER)
    public boolean deleteTemplate(@RestPath UUID templateId) {
        return templateRepository.deleteTemplate(templateId);
    }

    @POST
    @Path("/email/instant")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_USER)
    public InstantEmailTemplate createInstantEmailTemplate(@NotNull @Valid InstantEmailTemplate template) {
        return templateRepository.createInstantEmailTemplate(template);
    }

    @GET
    @Path("/email/instant")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(RBAC_INTERNAL_USER)
    public List<InstantEmailTemplate> getAllInstantEmailTemplates(@QueryParam("applicationId") UUID applicationId) {
        return templateRepository.findAllInstantEmailTemplates(applicationId);
    }

    @GET
    @Path("/email/instant/eventType/{eventTypeId}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(RBAC_INTERNAL_USER)
    public List<InstantEmailTemplate> getInstantEmailTemplates(@RestPath UUID eventTypeId) {
        return templateRepository.findInstantEmailTemplatesByEventType(eventTypeId);
    }

    @GET
    @Path("/email/instant/{templateId}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(RBAC_INTERNAL_USER)
    public InstantEmailTemplate getInstantEmailTemplate(@RestPath UUID templateId) {
        return templateRepository.findInstantEmailTemplateById(templateId);
    }

    @PUT
    @Path("/email/instant/{templateId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_USER)
    public Response updateInstantEmailTemplate(@RestPath UUID templateId, @NotNull @Valid InstantEmailTemplate template) {
        boolean updated = templateRepository.updateInstantEmailTemplate(templateId, template);
        if (updated) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @DELETE
    @Path("/email/instant/{templateId}")
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_USER)
    public boolean deleteInstantEmailTemplate(@RestPath UUID templateId) {
        return templateRepository.deleteInstantEmailTemplate(templateId);
    }

    @POST
    @Path("/email/aggregation")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_USER)
    public AggregationEmailTemplate createAggregationEmailTemplate(@NotNull @Valid AggregationEmailTemplate template) {
        return templateRepository.createAggregationEmailTemplate(template);
    }

    @GET
    @Path("/email/aggregation")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(RBAC_INTERNAL_USER)
    public List<AggregationEmailTemplate> getAllAggregationEmailTemplates() {
        return templateRepository.findAllAggregationEmailTemplates();
    }

    @GET
    @Path("/email/aggregation/application/{appId}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(RBAC_INTERNAL_USER)
    public List<AggregationEmailTemplate> getAggregationEmailTemplatesByApplication(@RestPath UUID appId) {
        return templateRepository.findAggregationEmailTemplatesByApplication(appId);
    }

    @GET
    @Path("/email/aggregation/{templateId}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(RBAC_INTERNAL_USER)
    public AggregationEmailTemplate getAggregationemailTemplate(@RestPath UUID templateId) {
        return templateRepository.findAggregationEmailTemplateById(templateId);
    }

    @PUT
    @Path("/email/aggregation/{templateId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_USER)
    public Response updateAggregationEmailTemplate(@RestPath UUID templateId, @NotNull @Valid AggregationEmailTemplate template) {
        boolean updated = templateRepository.updateAggregationEmailTemplate(templateId, template);
        if (updated) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @DELETE
    @Path("/email/aggregation/{templateId}")
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_USER)
    public boolean deleteAggregationEmailTemplate(@RestPath UUID templateId) {
        return templateRepository.deleteAggregationEmailTemplate(templateId);
    }

    @POST
    @Path("/email/render")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @APIResponses(value = {
            @APIResponse(responseCode = "200", content = {
                    @Content(schema = @Schema(title = "RenderEmailTemplateResponseSuccess", implementation = RenderEmailTemplateResponse.Success.class))
            }),
            @APIResponse(responseCode = "400", content = {
                    @Content(schema = @Schema(title = "RenderEmailTemplateResponseError", implementation = RenderEmailTemplateResponse.Error.class))
            })
    })
    @RolesAllowed(RBAC_INTERNAL_USER)
    public Response renderEmailTemplate(@NotNull @Valid RenderEmailTemplateRequest renderEmailTemplateRequest) {
        try {
            return templateEngineClient.render(renderEmailTemplateRequest);
        } catch (BadRequestException e) {
            // The following line is required to forward the HTTP 400 error message.
            return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
