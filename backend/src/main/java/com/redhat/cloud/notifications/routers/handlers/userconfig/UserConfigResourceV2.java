package com.redhat.cloud.notifications.routers.handlers.userconfig;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BundleRepository;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.models.dto.v2.subscriptions.ApplicationSubscriptionDTO;
import com.redhat.cloud.notifications.models.dto.v2.subscriptions.ApplicationSubscriptionUpdateDTO;
import com.redhat.cloud.notifications.models.dto.v2.subscriptions.BundleSubscriptionDTO;
import com.redhat.cloud.notifications.models.dto.v2.subscriptions.BundleSubscriptionUpdateDTO;
import com.redhat.cloud.notifications.models.dto.v2.subscriptions.EventTypeSubscriptionDTO;
import com.redhat.cloud.notifications.models.dto.v2.subscriptions.EventTypeSubscriptionUpdateDTO;
import com.redhat.cloud.notifications.models.dto.v2.subscriptions.SeverityDTO;
import com.redhat.cloud.notifications.models.dto.v2.subscriptions.SubscriptionChannelDTO;
import com.redhat.cloud.notifications.models.dto.v2.subscriptions.SubscriptionMapper;
import com.redhat.cloud.notifications.models.dto.v2.subscriptions.SubscriptionTypeDTO;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_2_0;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getUsername;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.isServiceAccountAuthentication;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class UserConfigResourceV2 {

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    BundleRepository bundleRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    SubscriptionMapper subscriptionMapper;

    @Inject
    BackendConfig backendConfig;

    @Path(API_NOTIFICATIONS_V_2_0 + "/user-config")
    public static class V2 extends UserConfigResourceV2 {
    }

    @GET
    @Path("/subscriptions")
    @Produces(APPLICATION_JSON)
    @Operation(
        operationId = "UserConfigResource$V2_getSubscriptions",
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
    public List<BundleSubscriptionDTO> getSubscriptions(
        @Context SecurityContext sec,
        @QueryParam("bundle") String bundleName,
        @QueryParam("application") String applicationName,
        @QueryParam("event_type") String eventTypeName
    ) {
        forbidAccessInCaseOfServiceAccountAuthentication(sec);

        if (bundleName == null && applicationName != null) {
            throw new BadRequestException("The 'application' query parameter requires 'bundle' to also be specified");
        }
        if ((bundleName == null || applicationName == null) && eventTypeName != null) {
            throw new BadRequestException("The 'event_type' query parameter requires both 'bundle' and 'application' to also be specified");
        }

        String orgId = getOrgId(sec);
        String username = getUsername(sec);

        // First fetch Bundles/Applications/EventTypes config tree
        List<BundleSubscriptionDTO> tree = new ArrayList<>();
        Map<EventTypeKey, EventTypeSubscriptionDTO> eventTypeIndex = new HashMap<>();
        for (Bundle bundle : resolveBundles(bundleName)) {
            List<ApplicationSubscriptionDTO> applicationDTOs = new ArrayList<>();
            for (Application application : resolveApplications(bundle, applicationName)) {
                List<EventTypeSubscriptionDTO> eventTypeDTOs = resolveEventTypes(bundle, application, eventTypeName).stream()
                    .map(this::buildDefaultEventTypeSubscription)
                    .collect(Collectors.toList());
                if (eventTypeDTOs.isEmpty()) {
                    continue;
                }
                for (EventTypeSubscriptionDTO eventTypeDTO : eventTypeDTOs) {
                    eventTypeIndex.put(new EventTypeKey(bundle.getName(), application.getName(), eventTypeDTO.getEventType()), eventTypeDTO);
                }
                ApplicationSubscriptionDTO applicationDTO = new ApplicationSubscriptionDTO();
                applicationDTO.setApplication(application.getName());
                applicationDTO.setApplicationDisplayName(application.getDisplayName());
                applicationDTO.setEventTypes(eventTypeDTOs);
                applicationDTOs.add(applicationDTO);
            }
            if (applicationDTOs.isEmpty()) {
                continue;
            }
            BundleSubscriptionDTO bundleDTO = new BundleSubscriptionDTO();
            bundleDTO.setBundle(bundle.getName());
            bundleDTO.setBundleDisplayName(bundle.getDisplayName());
            bundleDTO.setApplications(applicationDTOs);
            tree.add(bundleDTO);
        }

        // Second, update previous tree with user's subscriptions
        List<EventTypeEmailSubscription> subscriptions = subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(orgId, username);
        patchWithActualSubscriptions(eventTypeIndex, subscriptions);
        return tree;
    }

    // Keyed lookup into the tree built by getSubscriptions(), used by patchWithActualSubscriptions()
    // to find the right leaf for each stored subscription in O(1) instead of re-scanning the
    // applications/event-types lists of the tree per subscription row.
    private record EventTypeKey(String bundle, String application, String eventType) {
    }

    private List<Bundle> resolveBundles(String bundleName) {
        if (bundleName == null) {
            return bundleRepository.getBundles();
        }
        Bundle bundle = bundleRepository.getBundle(bundleName);
        if (bundle == null) {
            throw new NotFoundException(String.format("No bundle named '%s' found", bundleName));
        }
        return List.of(bundle);
    }

    private List<Application> resolveApplications(Bundle bundle, String applicationName) {
        if (applicationName == null) {
            return bundle.getApplications().stream()
                .sorted(Comparator.comparing(Application::getDisplayName))
                .collect(Collectors.toList());
        }
        Application application = applicationRepository.getApplication(bundle.getName(), applicationName);
        if (application == null) {
            throw new NotFoundException(String.format("No application named '%s' found in bundle '%s'", applicationName, bundle.getName()));
        }
        return List.of(application);
    }

    private List<EventType> resolveEventTypes(Bundle bundle, Application application, String eventTypeName) {
        if (eventTypeName == null) {
            return application.getEventTypes().stream()
                .sorted(Comparator.comparing(EventType::getDisplayName))
                .collect(Collectors.toList());
        }
        EventType eventType = applicationRepository.getEventType(bundle.getName(), application.getName(), eventTypeName);
        if (eventType == null) {
            throw new NotFoundException(String.format(
                "No event type named '%s' found for application '%s' in bundle '%s'", eventTypeName, application.getName(), bundle.getName()));
        }
        return List.of(eventType);
    }

    private EventTypeSubscriptionDTO buildDefaultEventTypeSubscription(EventType eventType) {
        EventTypeSubscriptionDTO dto = subscriptionMapper.eventTypeToEventTypeSubscriptionDTO(eventType);

        List<SubscriptionChannelDTO> channels = new ArrayList<>();
        for (SubscriptionType subscriptionType : SubscriptionType.values()) {
            boolean subscribedByDefault = subscriptionType.isSubscribedByDefault() || eventType.isSubscribedByDefault();
            List<SeverityDTO> subscribedSeverities = subscribedByDefault ? new ArrayList<>(dto.getAvailableSeverities()) : new ArrayList<>();
            channels.add(new SubscriptionChannelDTO(subscriptionMapper.subscriptionTypeToSubscriptionTypeDTO(subscriptionType), subscribedSeverities));
        }
        dto.setSubscriptions(channels);
        return dto;
    }

    private void patchWithActualSubscriptions(Map<EventTypeKey, EventTypeSubscriptionDTO> eventTypeIndex, List<EventTypeEmailSubscription> subscriptions) {
        for (EventTypeEmailSubscription subscription : subscriptions) {
            EventType eventType = subscription.getEventType();
            Application application = eventType.getApplication();
            Bundle bundle = application.getBundle();

            EventTypeSubscriptionDTO eventTypeDTO = eventTypeIndex.get(new EventTypeKey(bundle.getName(), application.getName(), eventType.getName()));
            if (eventTypeDTO == null) {
                continue;
            }
            SubscriptionTypeDTO channelType = subscriptionMapper.subscriptionTypeToSubscriptionTypeDTO(subscription.getType());
            SubscriptionChannelDTO channel = eventTypeDTO.getSubscriptions().stream()
                .filter(candidate -> candidate.getSubscriptionType() == channelType)
                .findFirst().orElse(null);
            if (channel == null) {
                continue;
            }

            Map<Severity, Boolean> severities = subscription.getSeverities();
            List<SeverityDTO> subscribedSeverities = severities == null ? List.of() : severities.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .sorted()
                .map(subscriptionMapper::severityToSeverityDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            channel.setSubscribedSeverities(subscribedSeverities);
        }
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
        operationId = "UserConfigResource$V2_updateSubscriptions",
        summary = "Bulk-update the authenticated user's notification subscriptions",
        description = "Partial update, not a full replace: any bundle, application, event type or channel omitted "
            + "from the request tree is left untouched rather than reset or unsubscribed."
    )
    public void updateSubscriptions(
        @Context SecurityContext sec,
        @NotNull @NotEmpty @Valid @RequestBody(content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = BundleSubscriptionUpdateDTO.class)))
            List<@NotNull BundleSubscriptionUpdateDTO> body
    ) {
        forbidAccessInCaseOfServiceAccountAuthentication(sec);

        String orgId = getOrgId(sec);
        String username = getUsername(sec);

        for (BundleSubscriptionUpdateDTO bundleUpdate : body) {
            for (ApplicationSubscriptionUpdateDTO applicationUpdate : bundleUpdate.getApplications()) {
                for (EventTypeSubscriptionUpdateDTO eventTypeUpdate : applicationUpdate.getEventTypes()) {
                    updateEventTypeSubscriptions(orgId, username, bundleUpdate.getBundle(), applicationUpdate.getApplication(), eventTypeUpdate);
                }
            }
        }
    }

    private void updateEventTypeSubscriptions(String orgId, String username, String bundleName, String applicationName, EventTypeSubscriptionUpdateDTO eventTypeUpdate) {
        EventType eventType = applicationRepository.getEventType(bundleName, applicationName, eventTypeUpdate.getEventType());
        if (eventType == null) {
            // BadRequestException (400), not NotFoundException (404) as the GET side of this resource
            // uses for the same condition: a PUT can touch many entities in one call, so an unknown
            // identifier here is treated as a client error to fix and retry in full, not a lookup miss.
            throw new BadRequestException(String.format(
                "No event type named '%s' found for application '%s' in bundle '%s'", eventTypeUpdate.getEventType(), applicationName, bundleName));
        }

        Set<Severity> availableSeverities = eventType.getAvailableSeverities();
        if (availableSeverities == null) {
            availableSeverities = Set.of();
        }
        for (SubscriptionChannelDTO channel : eventTypeUpdate.getSubscriptions()) {
            SubscriptionType subscriptionType = subscriptionMapper.subscriptionTypeDTOToSubscriptionType(channel.getSubscriptionType());
            if (subscriptionType == SubscriptionType.DRAWER && !backendConfig.isDrawerEnabled(orgId)) {
                // Mirrors the GET side (SubscriptionRepository#getAvailableTypes), which hides DRAWER
                // from an org until its Unleash flag is on. Without this, a write here would silently
                // not "take" from the caller's point of view: the GET would keep reporting the
                // hardcoded subscribed-by-default state instead of what was just written.
                continue;
            }

            Set<Severity> subscribedSeverities = channel.getSubscribedSeverities().stream()
                .map(subscriptionMapper::severityDTOToSeverity)
                .collect(Collectors.toSet());

            if (!availableSeverities.containsAll(subscribedSeverities)) {
                throw new BadRequestException(String.format(
                    "Invalid subscribed severities %s for event type '%s' (application '%s', bundle '%s', subscription type '%s'): available severities are %s",
                    subscribedSeverities, eventTypeUpdate.getEventType(), applicationName, bundleName, channel.getSubscriptionType(), availableSeverities));
            }

            Map<Severity, Boolean> severitiesMap = new HashMap<>();
            for (Severity severity : availableSeverities) {
                severitiesMap.put(severity, subscribedSeverities.contains(severity));
            }

            subscriptionRepository.updateSubscription(orgId, username, eventType.getId(), subscriptionType, !subscribedSeverities.isEmpty(), severitiesMap);
        }
    }

    private static void forbidAccessInCaseOfServiceAccountAuthentication(SecurityContext sec) {
        if (isServiceAccountAuthentication(sec)) {
            throw new ForbiddenException("This api can't be used with a service account authentication");
        }
    }
}
