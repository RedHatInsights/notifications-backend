package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.StartupUtils;
import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.BehaviorGroupResources;
import com.redhat.cloud.notifications.db.BundleResources;
import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.db.StatusResources;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.CurrentStatus;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.oapi.OApiFilter;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateRequest;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateResponse;
import com.redhat.cloud.notifications.routers.models.internal.RequestDefaultBehaviorGroupPropertyList;
import com.redhat.cloud.notifications.templates.TemplateEngineClient;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@Path(API_INTERNAL)
public class InternalService {

    private static final Logger LOGGER = Logger.getLogger(InternalService.class);
    private static final Pattern GIT_COMMIT_ID_PATTERN = Pattern.compile("git.commit.id.abbrev=([0-9a-f]{7})");

    @Inject
    BundleResources bundleResources;

    @Inject
    ApplicationResources appResources;

    @Inject
    BehaviorGroupResources behaviorGroupResources;

    @Inject
    EndpointResources endpointResources;

    @Inject
    StatusResources statusResources;

    @Inject
    OApiFilter oApiFilter;

    @Inject
    @RestClient
    TemplateEngineClient templateEngineClient;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Inject
    StartupUtils startupUtils;

    // This endpoint is used during the IQE tests to determine which version of the code is tested.
    @GET
    @Path("/version")
    public Uni<String> getVersion() {
        String gitProperties = startupUtils.readGitProperties();
        Matcher m = GIT_COMMIT_ID_PATTERN.matcher(gitProperties);
        if (m.matches()) {
            return Uni.createFrom().item(m.group(1));
        } else {
            LOGGER.infof("Git commit hash not found: %s", gitProperties);
            return Uni.createFrom().item("Git commit hash not found");
        }
    }

    @GET
    @Path("/")
    public void httpRoot() {
        throw new RedirectionException(Response.Status.OK, URI.create("index.html"));
    }

    @GET
    @Path("/openapi.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<String> serveInternalOpenAPI() {
        return oApiFilter.serveOpenApi(OApiFilter.INTERNAL);
    }

    @POST
    @Path("/bundles")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Uni<Bundle> createBundle(@NotNull @Valid Bundle bundle) {
        return sessionFactory.withSession(session -> {
            return bundleResources.createBundle(bundle);
        });
    }

    @GET
    @Path("/bundles")
    @Produces(APPLICATION_JSON)
    public Uni<List<Bundle>> getBundles() {
        return sessionFactory.withSession(session -> {
            // Return configured with types?
            return bundleResources.getBundles();
        });
    }

    @GET
    @Path("/bundles/{bundleId}")
    @Produces(APPLICATION_JSON)
    public Uni<Bundle> getBundle(@PathParam("bundleId") UUID bundleId) {
        return sessionFactory.withSession(session -> {
            return bundleResources.getBundle(bundleId)
                    .onItem().ifNull().failWith(new NotFoundException());
        });
    }

    @PUT
    @Path("/bundles/{bundleId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    public Uni<Response> updateBundle(@PathParam("bundleId") UUID bundleId, @NotNull @Valid Bundle bundle) {
        return sessionFactory.withSession(session -> {
            return bundleResources.updateBundle(bundleId, bundle)
                    .onItem().transform(rowCount -> {
                        if (rowCount == 0) {
                            return Response.status(Response.Status.NOT_FOUND).build();
                        } else {
                            return Response.ok().build();
                        }
                    });
        });
    }

    @DELETE
    @Path("/bundles/{bundleId}")
    @Produces(APPLICATION_JSON)
    public Uni<Boolean> deleteBundle(@PathParam("bundleId") UUID bundleId) {
        return sessionFactory.withSession(session -> {
            return bundleResources.deleteBundle(bundleId);
        });
    }

    @GET
    @Path("/bundles/{bundleId}/applications")
    @Produces(APPLICATION_JSON)
    public Uni<List<Application>> getApplications(@PathParam("bundleId") UUID bundleId) {
        return sessionFactory.withSession(session -> {
            return bundleResources.getApplications(bundleId);
        });
    }

    @POST
    @Path("/applications")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Uni<Application> createApplication(@NotNull @Valid Application app) {
        return sessionFactory.withSession(session -> {
            return appResources.createApp(app);
        });
    }

    @GET
    @Path("/applications/{appId}")
    @Produces(APPLICATION_JSON)
    public Uni<Application> getApplication(@PathParam("appId") UUID appId) {
        return sessionFactory.withSession(session -> {
            return appResources.getApplication(appId)
                    .onItem().ifNull().failWith(new NotFoundException());
        });
    }

    @PUT
    @Path("/applications/{appId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    public Uni<Response> updateApplication(@PathParam("appId") UUID appId, @NotNull @Valid Application app) {
        return sessionFactory.withSession(session -> {
            return appResources.updateApplication(appId, app)
                    .onItem().transform(rowCount -> {
                        if (rowCount == 0) {
                            return Response.status(Response.Status.NOT_FOUND).build();
                        } else {
                            return Response.ok().build();
                        }
                    });
        });
    }

    @DELETE
    @Path("/applications/{appId}")
    @Produces(APPLICATION_JSON)
    public Uni<Boolean> deleteApplication(@PathParam("appId") UUID appId) {
        return sessionFactory.withSession(session -> {
            return appResources.deleteApplication(appId);
        });
    }

    @GET
    @Path("/applications/{appId}/eventTypes")
    @Produces(APPLICATION_JSON)
    public Uni<List<EventType>> getEventTypes(@PathParam("appId") UUID appId) {
        return sessionFactory.withSession(session -> {
            return appResources.getEventTypes(appId);
        });
    }

    @POST
    @Path("/eventTypes")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Uni<EventType> createEventType(@NotNull @Valid EventType eventType) {
        return sessionFactory.withSession(session -> {
            return appResources.createEventType(eventType);
        });
    }

    @PUT
    @Path("/eventTypes/{eventTypeId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    public Uni<Response> updateEventType(@PathParam("eventTypeId") UUID eventTypeId, @NotNull @Valid EventType eventType) {
        return sessionFactory.withSession(session -> {
            return appResources.updateEventType(eventTypeId, eventType)
                    .onItem().transform(rowCount -> {
                        if (rowCount == 0) {
                            return Response.status(Response.Status.NOT_FOUND).build();
                        } else {
                            return Response.ok().build();
                        }
                    });
        });
    }

    @DELETE
    @Path("/eventTypes/{eventTypeId}")
    @Produces(APPLICATION_JSON)
    public Uni<Boolean> deleteEventType(@PathParam("eventTypeId") UUID eventTypeId) {
        return sessionFactory.withSession(session -> {
            return appResources.deleteEventTypeById(eventTypeId);
        });
    }

    @PUT
    @Path("/status")
    @Consumes(APPLICATION_JSON)
    public Uni<Void> setCurrentStatus(@NotNull @Valid CurrentStatus status) {
        return sessionFactory.withSession(session -> {
            return statusResources.setCurrentStatus(status);
        });
    }

    @POST
    @Path("/templates/email/render")
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
    public Uni<Response> renderEmailTemplate(@NotNull @Valid RenderEmailTemplateRequest renderEmailTemplateRequest) {
        return templateEngineClient.render(renderEmailTemplateRequest)
                // The following line is required to forward the HTTP 400 error message.
                .onFailure(BadRequestException.class).recoverWithItem(throwable -> Response.status(BAD_REQUEST).entity(throwable.getMessage()).build());
    }

    @GET
    @Path("/behaviorGroups/default")
    @Produces(APPLICATION_JSON)
    public Uni<List<BehaviorGroup>> getDefaultBehaviorGroups() {
        return sessionFactory.withSession(session -> {
            return behaviorGroupResources.findDefaults();
        });
    }

    @POST
    @Path("/behaviorGroups/default")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Uni<BehaviorGroup> createDefaultBehaviorGroup(@NotNull @Valid BehaviorGroup behaviorGroup) {
        return sessionFactory.withSession(session -> {
            return behaviorGroupResources.createDefault(behaviorGroup);
        });
    }

    @PUT
    @Path("/behaviorGroups/default/{id}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Update a default behavior group.")
    public Uni<Boolean> updateDefaultBehaviorGroup(@PathParam("id") UUID id, @NotNull @Valid BehaviorGroup behaviorGroup) {
        return sessionFactory.withSession(session -> {
            behaviorGroup.setId(id);
            return behaviorGroupResources.updateDefault(behaviorGroup);
        });
    }

    @DELETE
    @Path("/behaviorGroups/default/{id}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Deletes a default behavior group.")
    public Uni<Boolean> deleteDefaultBehaviorGroup(@PathParam("id") UUID id) {
        return sessionFactory.withSession(session -> behaviorGroupResources.deleteDefault(id));
    }

    @PUT
    @Path("/behaviorGroups/default/{behaviorGroupId}/actions")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Update the list of actions of a default behavior group.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> updateDefaultBehaviorGroupActions(@PathParam("behaviorGroupId") UUID behaviorGroupId, List<RequestDefaultBehaviorGroupPropertyList> propertiesList) {
        if (propertiesList == null) {
            return Uni.createFrom().failure(new BadRequestException("The request body must contain a list of EmailSubscriptionProperties"));
        }

        if (propertiesList.size() != propertiesList.stream().distinct().count()) {
            return Uni.createFrom().failure(new BadRequestException("The list of EmailSubscriptionProperties should not contain duplicates"));
        }

        return sessionFactory.withSession(session -> {
            return Multi.createFrom().iterable(
                    propertiesList.stream().map(p -> {
                        EmailSubscriptionProperties properties = new EmailSubscriptionProperties();
                        properties.setOnlyAdmins(p.isOnlyAdmins());
                        properties.setIgnorePreferences(p.isIgnorePreferences());
                        return properties;
                    })
                    .collect(Collectors.toList())
                )
                // order matters
                .onItem().transformToUniAndConcatenate(
                    properties -> endpointResources.getOrCreateEmailSubscriptionEndpoint(null, properties, false)
                ).collect().asList()
                .onItem().transformToUni(endpoints -> behaviorGroupResources.updateDefaultBehaviorGroupActions(
                        behaviorGroupId,
                        endpoints.stream().distinct().map(Endpoint::getId).collect(Collectors.toList())
                    ))
                    .onItem().transform(status -> Response.status(status).build());
        });
    }

    @PUT
    @Path("/behaviorGroups/default/{behaviorGroupId}/eventType/{eventTypeId}")
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Links the default behavior group to the event type.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> linkDefaultBehaviorToEventType(@PathParam("behaviorGroupId") UUID behaviorGroupId, @PathParam("eventTypeId") UUID eventTypeId) {
        return behaviorGroupResources.linkEventTypeDefaultBehavior(eventTypeId, behaviorGroupId)
                .onItem().transform(isSuccess -> {
                    if (isSuccess) {
                        return Response.ok().build();
                    } else {
                        return Response.status(BAD_REQUEST).build();
                    }
                });
    }

    @DELETE
    @Path("/behaviorGroups/default/{behaviorGroupId}/eventType/{eventTypeId}")
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Unlinks the default behavior group from the event type.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> unlinkDefaultBehaviorToEventType(@PathParam("behaviorGroupId") UUID behaviorGroupId, @PathParam("eventTypeId") UUID eventTypeId) {
        return behaviorGroupResources.unlinkEventTypeDefaultBehavior(eventTypeId, behaviorGroupId)
                .onItem().transform(isSuccess -> {
                    if (isSuccess) {
                        return Response.ok().build();
                    } else {
                        return Response.status(BAD_REQUEST).build();
                    }
                });
    }
}
