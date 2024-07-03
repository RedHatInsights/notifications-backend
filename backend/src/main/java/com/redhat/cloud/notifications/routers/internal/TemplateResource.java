package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.auth.kessel.KesselAuthorization;
import com.redhat.cloud.notifications.auth.kessel.ResourceType;
import com.redhat.cloud.notifications.auth.kessel.WorkspacePermission;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.Template;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateRequest;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateResponse;
import com.redhat.cloud.notifications.templates.TemplateEngineClient;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestPath;

import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_INTERNAL_USER;
import static com.redhat.cloud.notifications.auth.kessel.Constants.WORKSPACE_ID_PLACEHOLDER;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;

@Path(API_INTERNAL + "/templates")
public class TemplateResource {
    @Inject
    BackendConfig backendConfig;

    @Inject
    KesselAuthorization kesselAuthorization;

    @Inject
    TemplateRepository templateRepository;

    @Inject
    @RestClient
    TemplateEngineClient templateEngineClient;

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public Template createTemplate(@Context final SecurityContext securityContext,  @NotNull @Valid Template template) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalCreateTemplate(template);
        } else {
            return this.legacyRBACcreateTemplate(template);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_ADMIN)
    public Template legacyRBACcreateTemplate(final Template template) {
        return this.internalCreateTemplate(template);
    }

    public Template internalCreateTemplate(final Template template) {
        return this.templateRepository.createTemplate(template);
    }

    @GET
    @Produces(APPLICATION_JSON)
    public List<Template> getAllTemplates(@Context final SecurityContext securityContext) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalGetAllTemplates();
        } else {
            return this.legacyRBACGetAllTemplates();
        }
    }

    @RolesAllowed(RBAC_INTERNAL_USER)
    public List<Template> legacyRBACGetAllTemplates() {
        return internalGetAllTemplates();
    }

    public List<Template> internalGetAllTemplates() {
        return this.templateRepository.findAllTemplates();
    }

    @GET
    @Path("/{templateId}")
    @Produces(APPLICATION_JSON)
    public Template getTemplate(@Context final SecurityContext securityContext, @RestPath UUID templateId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalGetTemplate(templateId);
        } else {
            return this.legacyRBACGetTemplate(templateId);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_USER)
    public Template legacyRBACGetTemplate(final UUID templateId) {
        return this.internalGetTemplate(templateId);
    }

    public Template internalGetTemplate(final UUID templateId) {
        return this.templateRepository.findTemplateById(templateId);
    }

    @PUT
    @Path("/{templateId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Transactional
    public Response updateTemplate(@Context final SecurityContext securityContext, @RestPath UUID templateId, Template template) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalUpdateTemplate(templateId, template);
        } else {
            return this.legacyRBACUpdateTemplate(templateId, template);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_ADMIN)
    public Response legacyRBACUpdateTemplate(final UUID templateId, final Template template) {
        return this.internalUpdateTemplate(templateId, template);
    }

    public Response internalUpdateTemplate(final UUID templateId, final Template template) {
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
    public boolean deleteTemplate(@Context final SecurityContext securityContext, @RestPath UUID templateId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalDeleteTemplate(templateId);
        } else {
            return this.legacyRBACDeleteTemplate(templateId);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_ADMIN)
    public boolean legacyRBACDeleteTemplate(final UUID templateId) {
        return this.internalDeleteTemplate(templateId);
    }

    public boolean internalDeleteTemplate(final UUID templateId) {
        return this.templateRepository.deleteTemplate(templateId);
    }

    @POST
    @Path("/email/instant")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public InstantEmailTemplate createInstantEmailTemplate(@Context final SecurityContext securityContext, @NotNull @Valid InstantEmailTemplate template) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalCreateInstantEmailTemplate(template);
        } else {
            return this.legacyRBACInternalCreateInstantEmailTemplate(template);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_ADMIN)
    public InstantEmailTemplate legacyRBACInternalCreateInstantEmailTemplate(final InstantEmailTemplate template) {
        return this.internalCreateInstantEmailTemplate(template);
    }

    public InstantEmailTemplate internalCreateInstantEmailTemplate(final InstantEmailTemplate template) {
        return this.templateRepository.createInstantEmailTemplate(template);
    }

    @GET
    @Path("/email/instant")
    @Produces(APPLICATION_JSON)
    public List<InstantEmailTemplate> getAllInstantEmailTemplates(@Context final SecurityContext securityContext, @QueryParam("applicationId") UUID applicationId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalGetAllInstantEmailTemplates(applicationId);
        } else {
            return this.legacyRBACGetAllInstantEmailTemplates(applicationId);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_USER)
    public List<InstantEmailTemplate> legacyRBACGetAllInstantEmailTemplates(final UUID applicationId) {
        return this.internalGetAllInstantEmailTemplates(applicationId);
    }

    public List<InstantEmailTemplate> internalGetAllInstantEmailTemplates(final UUID applicationId) {
        return this.templateRepository.findAllInstantEmailTemplates(applicationId);
    }

    @GET
    @Path("/email/instant/eventType/{eventTypeId}")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = InstantEmailTemplate.class))),
        @APIResponse(responseCode = "404", description = "No instant email found for the event type", content = @Content(mediaType = TEXT_PLAIN, schema = @Schema(type = SchemaType.STRING)))
    })
    public InstantEmailTemplate getInstantEmailTemplateByEventType(@Context final SecurityContext securityContext, @RestPath UUID eventTypeId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalGetInstantEmailTemplateByEventType(eventTypeId);
        } else {
            return this.legacyRBACGetInstantEmailTemplateByEventType(eventTypeId);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_USER)
    public InstantEmailTemplate legacyRBACGetInstantEmailTemplateByEventType(final UUID eventTypeId) {
        return this.internalGetInstantEmailTemplateByEventType(eventTypeId);
    }

    public InstantEmailTemplate internalGetInstantEmailTemplateByEventType(final UUID eventTypeId) {
        return this.templateRepository.findInstantEmailTemplateByEventType(eventTypeId);
    }

    @GET
    @Path("/email/instant/{templateId}")
    @Produces(APPLICATION_JSON)
    public InstantEmailTemplate getInstantEmailTemplate(@Context final SecurityContext securityContext, @RestPath UUID templateId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalGetInstantEmailTemplate(templateId);
        } else {
            return this.legacyRBACGetInstantEmailTemplate(templateId);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_USER)
    public InstantEmailTemplate legacyRBACGetInstantEmailTemplate(final UUID templateId) {
        return this.internalGetInstantEmailTemplate(templateId);
    }

    public InstantEmailTemplate internalGetInstantEmailTemplate(final UUID templateId) {
        return this.templateRepository.findInstantEmailTemplateById(templateId);
    }

    @PUT
    @Path("/email/instant/{templateId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Transactional
    public Response updateInstantEmailTemplate(@Context final SecurityContext securityContext, @RestPath UUID templateId, @NotNull @Valid InstantEmailTemplate template) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalUpdateInstantEmailTemplate(templateId, template);
        } else {
            return this.legacyRBACUpdateInstantEmailTemplate(templateId, template);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_ADMIN)
    public Response legacyRBACUpdateInstantEmailTemplate(final UUID templateId, final InstantEmailTemplate template) {
        return this.internalUpdateInstantEmailTemplate(templateId, template);
    }

    public Response internalUpdateInstantEmailTemplate(final UUID templateId, final InstantEmailTemplate template) {
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
    public boolean deleteInstantEmailTemplate(@Context final SecurityContext securityContext, @RestPath UUID templateId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalDeleteInstantEmailTemplate(templateId);
        } else {
            return this.legacyRBACDeleteInstantEmailTemplate(templateId);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_ADMIN)
    public boolean legacyRBACDeleteInstantEmailTemplate(final UUID templateId) {
        return this.internalDeleteInstantEmailTemplate(templateId);
    }

    public boolean internalDeleteInstantEmailTemplate(final UUID templateId) {
        return this.templateRepository.deleteInstantEmailTemplate(templateId);
    }

    @POST
    @Path("/email/aggregation")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_ADMIN)
    public AggregationEmailTemplate createAggregationEmailTemplate(@Context final SecurityContext securityContext, @NotNull @Valid AggregationEmailTemplate template) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalCreateAggregationEmailTemplate(template);
        } else {
            return this.legacyRBACCreateAggregationEmailTemplate(template);
        }
    }

    public AggregationEmailTemplate legacyRBACCreateAggregationEmailTemplate(final AggregationEmailTemplate template) {
        return this.internalCreateAggregationEmailTemplate(template);
    }

    public AggregationEmailTemplate internalCreateAggregationEmailTemplate(final AggregationEmailTemplate template) {
        return this.templateRepository.createAggregationEmailTemplate(template);
    }

    @GET
    @Path("/email/aggregation")
    @Produces(APPLICATION_JSON)
    public List<AggregationEmailTemplate> getAllAggregationEmailTemplates(@Context final SecurityContext securityContext) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalGetAllAggregationEmailTemplates();
        } else {
            return this.legacyRBACGetAllAggregationEmailTemplates();
        }
    }

    @RolesAllowed(RBAC_INTERNAL_USER)
    public List<AggregationEmailTemplate> legacyRBACGetAllAggregationEmailTemplates() {
        return this.internalGetAllAggregationEmailTemplates();
    }

    public List<AggregationEmailTemplate> internalGetAllAggregationEmailTemplates() {
        return this.templateRepository.findAllAggregationEmailTemplates();
    }

    @GET
    @Path("/email/aggregation/application/{appId}")
    @Produces(APPLICATION_JSON)
    public List<AggregationEmailTemplate> getAggregationEmailTemplatesByApplication(@Context final SecurityContext securityContext, @RestPath UUID appId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalGetAggregationEmailTemplatesByApplication(appId);
        } else {
            return this.legacyRBACGetAggregationEmailTemplatesByApplication(appId);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_USER)
    public List<AggregationEmailTemplate> legacyRBACGetAggregationEmailTemplatesByApplication(final UUID appId) {
        return this.internalGetAggregationEmailTemplatesByApplication(appId);
    }

    public List<AggregationEmailTemplate> internalGetAggregationEmailTemplatesByApplication(final UUID appId) {
        return this.templateRepository.findAggregationEmailTemplatesByApplication(appId);
    }

    @GET
    @Path("/email/aggregation/{templateId}")
    @Produces(APPLICATION_JSON)
    public AggregationEmailTemplate getAggregationemailTemplate(@Context final SecurityContext securityContext, @RestPath UUID templateId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalGetAggregationemailTemplate(templateId);
        } else {
            return this.legacyRBACGetAggregationemailTemplate(templateId);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_USER)
    public AggregationEmailTemplate legacyRBACGetAggregationemailTemplate(final UUID templateId) {
        return this.internalGetAggregationemailTemplate(templateId);
    }

    public AggregationEmailTemplate internalGetAggregationemailTemplate(final UUID templateId) {
        return this.templateRepository.findAggregationEmailTemplateById(templateId);
    }

    @PUT
    @Path("/email/aggregation/{templateId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Transactional
    public Response updateAggregationEmailTemplate(@Context final SecurityContext securityContext, @RestPath UUID templateId, @NotNull @Valid AggregationEmailTemplate template) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalUpdateAggregationEmailTemplate(templateId, template);
        } else {
            return this.legacyRBACUpdateAggregationEmailTemplate(templateId, template);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_ADMIN)
    public Response legacyRBACUpdateAggregationEmailTemplate(final UUID templateId, final AggregationEmailTemplate template) {
        return this.internalUpdateAggregationEmailTemplate(templateId, template);
    }

    public Response internalUpdateAggregationEmailTemplate(final UUID templateId, final AggregationEmailTemplate template) {
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
    public boolean deleteAggregationEmailTemplate(@Context final SecurityContext securityContext, @RestPath UUID templateId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalDeleteAggregationEmailTemplate(templateId);
        } else {
            return this.legacyRBACDeleteAggregationEmailTemplate(templateId);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_ADMIN)
    public boolean legacyRBACDeleteAggregationEmailTemplate(final UUID templateId) {
        return this.internalDeleteAggregationEmailTemplate(templateId);
    }

    public boolean internalDeleteAggregationEmailTemplate(final UUID templateId) {
        return this.templateRepository.deleteAggregationEmailTemplate(templateId);
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

    public Response renderEmailTemplate(@Context final SecurityContext securityContext, @NotNull @Valid RenderEmailTemplateRequest renderEmailTemplateRequest) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalRenderEmailTemplate(renderEmailTemplateRequest);
        } else {
            return this.legacyRBACRenderEmailTemplate(renderEmailTemplateRequest);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_USER)
    public Response legacyRBACRenderEmailTemplate(final RenderEmailTemplateRequest renderEmailTemplateRequest) {
        return this.internalRenderEmailTemplate(renderEmailTemplateRequest);
    }

    public Response internalRenderEmailTemplate(final RenderEmailTemplateRequest renderEmailTemplateRequest) {
        try {
            return templateEngineClient.render(renderEmailTemplateRequest);
        } catch (BadRequestException e) {
            // The following line is required to forward the HTTP 400 error message.
            return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
