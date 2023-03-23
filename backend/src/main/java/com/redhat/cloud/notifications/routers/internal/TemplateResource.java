package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.Template;
import com.redhat.cloud.notifications.routers.SecurityContextUtil;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateRequest;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateResponse;
import com.redhat.cloud.notifications.templates.TemplateEngineClient;
import io.quarkus.security.ForbiddenException;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

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
    SecurityContextUtil securityContextUtil;

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
    public Template createTemplate(@Context SecurityContext sec, @NotNull @Valid Template template) {
        // Common templates don't have any applicationId, only internal admins can manage them
        securityContextUtil.hasPermissionForApplication(sec, template.getApplicationId());
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
    public Response updateTemplate(@Context SecurityContext sec, @RestPath UUID templateId, Template template) {
        Template templateFromDb = templateRepository.findTemplateById(templateId);
        // Common templates don't have any applicationId, only internal admins can manage them
        if (!sec.isUserInRole(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)) {
            if (null == templateFromDb.getApplication() || null == template.getApplicationId()) {
                throw new ForbiddenException();
            }
            // check if user have permissions to update existing template
            securityContextUtil.hasPermissionForApplication(sec, templateFromDb.getApplication().getId());
            // if user change template application, he must have permissions on the new App
            securityContextUtil.hasPermissionForApplication(sec, template.getApplicationId());
        }
        boolean updated = templateRepository.updateTemplate(templateId, template);
        if (updated) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @DELETE
    @Path("/{templateId}")
    @Produces(TEXT_PLAIN)
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_USER)
    public boolean deleteTemplate(@Context SecurityContext sec, @RestPath UUID templateId) {
        Template templateFromDb = templateRepository.findTemplateById(templateId);
        // Common templates don't have any applicationId, only internal admins can manage them
        if (!sec.isUserInRole(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)) {
            if (null == templateFromDb.getApplication()) {
                throw new ForbiddenException();
            }
            securityContextUtil.hasPermissionForApplication(sec, templateFromDb.getApplication().getId());
        }
        return templateRepository.deleteTemplate(templateId);
    }

    @POST
    @Path("/email/instant")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_USER)
    public InstantEmailTemplate createInstantEmailTemplate(@Context SecurityContext sec, @NotNull @Valid InstantEmailTemplate template) {
        if (null != template.getEventTypeId()) {
            EventType eventType = templateRepository.findEventType(template.getEventTypeId());
            if (null != eventType) {
                securityContextUtil.hasPermissionForApplication(sec, eventType.getApplicationId());
            }
        }
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
    @RolesAllowed(RBAC_INTERNAL_USER)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = InstantEmailTemplate.class))),
        @APIResponse(responseCode = "404", description = "No instant email found for the event type", content = @Content(mediaType = TEXT_PLAIN, schema = @Schema(type = SchemaType.STRING)))
    })
    public InstantEmailTemplate getInstantEmailTemplateByEventType(@RestPath UUID eventTypeId) {
        return templateRepository.findInstantEmailTemplateByEventType(eventTypeId);
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
    public Response updateInstantEmailTemplate(@Context SecurityContext sec, @RestPath UUID templateId, @NotNull @Valid InstantEmailTemplate template) {
        // check if user can link existing instant email template to the new event type
        if (null != template.getEventTypeId()) {
            EventType eventType = templateRepository.findEventType(template.getEventTypeId());
            if (null != eventType) {
                securityContextUtil.hasPermissionForApplication(sec, eventType.getApplicationId());
            }
        }

        // check if user have permission to update existing instant email template
        InstantEmailTemplate instantEmailTemplate = templateRepository.findInstantEmailTemplateById(templateId);
        if (null != instantEmailTemplate && null != instantEmailTemplate.getEventTypeId()) {
            EventType eventType = templateRepository.findEventType(instantEmailTemplate.getEventTypeId());
            if (null != eventType) {
                securityContextUtil.hasPermissionForApplication(sec, eventType.getApplicationId());
            }
        }

        boolean updated = templateRepository.updateInstantEmailTemplate(templateId, template);
        if (updated) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @DELETE
    @Path("/email/instant/{templateId}")
    @Produces(TEXT_PLAIN)
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_USER)
    public boolean deleteInstantEmailTemplate(@Context SecurityContext sec, @RestPath UUID templateId) {
        InstantEmailTemplate instantEmailTemplate = templateRepository.findInstantEmailTemplateById(templateId);
        if (null != instantEmailTemplate && null != instantEmailTemplate.getEventTypeId()) {
            EventType eventType = templateRepository.findEventType(instantEmailTemplate.getEventTypeId());
            if (null != eventType) {
                securityContextUtil.hasPermissionForApplication(sec, eventType.getApplicationId());
            }
        }
        return templateRepository.deleteInstantEmailTemplate(templateId);
    }

    @POST
    @Path("/email/aggregation")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_USER)
    public AggregationEmailTemplate createAggregationEmailTemplate(@Context SecurityContext sec, @NotNull @Valid AggregationEmailTemplate template) {
        if (null != template.getApplicationId()) {
            securityContextUtil.hasPermissionForApplication(sec, template.getApplicationId());
        }
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
    public Response updateAggregationEmailTemplate(@Context SecurityContext sec, @RestPath UUID templateId, @NotNull @Valid AggregationEmailTemplate template) {
        AggregationEmailTemplate templateFromDb = templateRepository.findAggregationEmailTemplateById(templateId);
        if (null != templateFromDb.getApplicationId()) {
            // check if user have permissions to update existing template
            securityContextUtil.hasPermissionForApplication(sec, templateFromDb.getApplication().getId());
        }

        if (null != template.getApplicationId()) {
            // if user change template application, he must have permissions on the new App
            securityContextUtil.hasPermissionForApplication(sec, template.getApplicationId());
        }

        boolean updated = templateRepository.updateAggregationEmailTemplate(templateId, template);
        if (updated) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @DELETE
    @Path("/email/aggregation/{templateId}")
    @Produces(TEXT_PLAIN)
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_USER)
    public boolean deleteAggregationEmailTemplate(@Context SecurityContext sec, @RestPath UUID templateId) {
        AggregationEmailTemplate aggregationEmailTemplate = templateRepository.findAggregationEmailTemplateById(templateId);
        if (null != aggregationEmailTemplate.getApplication()) {
            securityContextUtil.hasPermissionForApplication(sec, aggregationEmailTemplate.getApplication().getId());
        }

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
