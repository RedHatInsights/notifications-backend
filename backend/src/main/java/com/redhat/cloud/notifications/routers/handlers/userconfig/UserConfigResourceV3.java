package com.redhat.cloud.notifications.routers.handlers.userconfig;

import com.redhat.cloud.notifications.models.dto.v3.subscriptions.BundleSubscriptionDTO;
import com.redhat.cloud.notifications.models.dto.v3.subscriptions.BundleSubscriptionUpdateDTO;
import com.redhat.cloud.notifications.models.dto.v3.subscriptions.SubscriptionMapper;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import java.util.List;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_3_0;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class UserConfigResourceV3 extends UserConfigResourceCommon {

    @Inject
    SubscriptionMapper v3SubscriptionMapper;

    @Path(API_NOTIFICATIONS_V_3_0 + "/user-config")
    public static class V3 extends UserConfigResourceV3 {
    }

    @GET
    @Path("/subscriptions")
    @Produces(APPLICATION_JSON)
    @Operation(
        operationId = "UserConfigResource$V3_getSubscriptions",
        summary = "Retrieve the authenticated user's notification subscriptions",
        description = "Returns the authenticated user's subscriptions as a bundle/application/event type/channel tree. "
            + "Query params progressively narrow the returned tree; each requires its parent to also be specified."
    )
    @Parameter(name = "bundle", description = "Restrict the response to this bundle")
    @Parameter(name = "application", description = "Restrict the response to this application; requires 'bundle'")
    @Parameter(name = "event_type", description = "Restrict the response to this event type; requires 'bundle' and 'application'")
    @APIResponse(responseCode = "200", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = BundleSubscriptionDTO.class)))
    @APIResponse(responseCode = "400", description = "A query parameter was specified without its required parent (e.g. 'application' without 'bundle')")
    @APIResponse(responseCode = "404", description = "The named bundle, application or event type doesn't exist")
    public List<BundleSubscriptionDTO> getV3Subscriptions(
        @Context SecurityContext sec,
        @QueryParam("bundle") String bundleName,
        @QueryParam("application") String applicationName,
        @QueryParam("event_type") String eventTypeName
    ) {
        return v3SubscriptionMapper.v2ToV3Bundles(getSubscriptions(sec, bundleName, applicationName, eventTypeName));
    }

    @PUT
    @Path("/subscriptions")
    @Consumes(APPLICATION_JSON)
    @Transactional
    @APIResponse(responseCode = "204")
    @APIResponse(responseCode = "400", description = "A bundle, application or event type in the request doesn't exist, or requests severities "
        + "outside the event type's available_severities. Unlike the read-only GET (which reports an unknown bundle/application/event type as "
        + "404), this bulk write endpoint reports it as 400: it's a client error to fix and retry in full, not a missing resource to look up.")
    @Operation(
        operationId = "UserConfigResource$V3_updateSubscriptions",
        summary = "Bulk-update the authenticated user's notification subscriptions",
        description = "Partial update, not a full replace: any bundle, application, event type or channel omitted "
            + "from the request tree is left untouched rather than reset or unsubscribed."
    )
    public void updateV3Subscriptions(
        @Context SecurityContext sec,
        @NotNull @Valid @RequestBody(content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = BundleSubscriptionUpdateDTO.class)))
            List<@NotNull BundleSubscriptionUpdateDTO> body
    ) {
        doUpdateSubscriptions(sec, v3SubscriptionMapper.v3ToV2BundleUpdates(body));
    }
}
