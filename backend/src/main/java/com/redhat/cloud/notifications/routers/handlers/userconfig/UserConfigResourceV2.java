package com.redhat.cloud.notifications.routers.handlers.userconfig;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.auth.annotation.Authorization;
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
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
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
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_2_0;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.NOTIFICATIONS_EDIT;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.NOTIFICATIONS_VIEW;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getUsername;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class UserConfigResourceV2 {

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    BundleRepository bundleRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    BackendConfig backendConfig;

    @Inject
    SubscriptionMapper subscriptionMapper;

    @Path(API_NOTIFICATIONS_V_2_0 + "/user-config")
    public static class V2 extends UserConfigResourceV2 {
    }

    @GET
    @Path("/subscriptions")
    @Produces(APPLICATION_JSON)
    @Operation(operationId = "UserConfigResource$V2_getSubscriptions", summary = "Retrieve the authenticated user's notification subscriptions")
    @Authorization(legacyRBACRole = RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW)
    public List<BundleSubscriptionDTO> getSubscriptions(
        @Context SecurityContext sec,
        @QueryParam("bundle") String bundleName,
        @QueryParam("application") String applicationName,
        @QueryParam("event_type") String eventTypeName
    ) {
        if (bundleName == null && applicationName != null) {
            throw new BadRequestException("The 'application' query parameter requires 'bundle' to also be specified");
        }
        if ((bundleName == null || applicationName == null) && eventTypeName != null) {
            throw new BadRequestException("The 'event_type' query parameter requires both 'bundle' and 'application' to also be specified");
        }

        String orgId = getOrgId(sec);
        String username = getUsername(sec);
        boolean showHiddenEventTypes = backendConfig.isShowHiddenEventTypes(orgId);

        List<BundleSubscriptionDTO> tree = new ArrayList<>();
        for (Bundle bundle : resolveBundles(bundleName)) {
            List<ApplicationSubscriptionDTO> applicationDTOs = new ArrayList<>();
            for (Application application : resolveApplications(bundle, applicationName)) {
                List<EventTypeSubscriptionDTO> eventTypeDTOs = resolveEventTypes(bundle, application, eventTypeName, showHiddenEventTypes).stream()
                    .map(this::buildDefaultEventTypeSubscription)
                    .collect(Collectors.toList());
                if (eventTypeDTOs.isEmpty()) {
                    continue;
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

        List<EventTypeEmailSubscription> subscriptions = subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(orgId, username);
        patchWithActualSubscriptions(tree, subscriptions);
        return tree;
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

    private List<EventType> resolveEventTypes(Bundle bundle, Application application, String eventTypeName, boolean showHiddenEventTypes) {
        if (eventTypeName == null) {
            return application.getEventTypes().stream()
                .filter(eventType -> eventType.isVisible() || showHiddenEventTypes)
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
        List<SeverityDTO> availableSeverities = eventType.getAvailableSeverities().stream()
            .sorted()
            .map(subscriptionMapper::severityToSeverityDTO)
            .collect(Collectors.toList());

        List<SubscriptionChannelDTO> channels = new ArrayList<>();
        for (SubscriptionType subscriptionType : SubscriptionType.values()) {
            boolean subscribedByDefault = subscriptionType.isSubscribedByDefault() || eventType.isSubscribedByDefault();
            List<SeverityDTO> subscribedSeverities = subscribedByDefault ? new ArrayList<>(availableSeverities) : new ArrayList<>();
            channels.add(new SubscriptionChannelDTO(subscriptionMapper.subscriptionTypeToSubscriptionTypeDTO(subscriptionType), subscribedSeverities));
        }

        EventTypeSubscriptionDTO dto = new EventTypeSubscriptionDTO();
        dto.setEventType(eventType.getName());
        dto.setDisplayName(eventType.getDisplayName());
        dto.setAvailableSeverities(availableSeverities);
        dto.setSubscriptions(channels);
        return dto;
    }

    private void patchWithActualSubscriptions(List<BundleSubscriptionDTO> tree, List<EventTypeEmailSubscription> subscriptions) {
        Map<String, BundleSubscriptionDTO> bundleByName = tree.stream()
            .collect(Collectors.toMap(BundleSubscriptionDTO::getBundle, Function.identity()));

        for (EventTypeEmailSubscription subscription : subscriptions) {
            EventType eventType = subscription.getEventType();
            Application application = eventType.getApplication();
            Bundle bundle = application.getBundle();

            BundleSubscriptionDTO bundleDTO = bundleByName.get(bundle.getName());
            if (bundleDTO == null) {
                continue;
            }
            ApplicationSubscriptionDTO applicationDTO = bundleDTO.getApplications().stream()
                .filter(candidate -> candidate.getApplication().equals(application.getName()))
                .findFirst().orElse(null);
            if (applicationDTO == null) {
                continue;
            }
            EventTypeSubscriptionDTO eventTypeDTO = applicationDTO.getEventTypes().stream()
                .filter(candidate -> candidate.getEventType().equals(eventType.getName()))
                .findFirst().orElse(null);
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
                .collect(Collectors.toList());
            channel.setSubscribedSeverities(subscribedSeverities);
        }
    }

    @PUT
    @Path("/subscriptions")
    @Consumes(APPLICATION_JSON)
    @Transactional
    @APIResponse(responseCode = "204")
    @Operation(operationId = "UserConfigResource$V2_updateSubscriptions", summary = "Bulk-update the authenticated user's notification subscriptions")
    @Authorization(legacyRBACRole = RBAC_WRITE_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_EDIT)
    public void updateSubscriptions(
        @Context SecurityContext sec,
        @NotNull @Valid @RequestBody(content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = BundleSubscriptionUpdateDTO.class)))
            List<BundleSubscriptionUpdateDTO> body
    ) {
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
            throw new BadRequestException(String.format(
                "No event type named '%s' found for application '%s' in bundle '%s'", eventTypeUpdate.getEventType(), applicationName, bundleName));
        }

        Set<Severity> availableSeverities = eventType.getAvailableSeverities();
        for (SubscriptionChannelDTO channel : eventTypeUpdate.getSubscriptions()) {
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

            SubscriptionType subscriptionType = subscriptionMapper.subscriptionTypeDTOToSubscriptionType(channel.getSubscriptionType());
            subscriptionRepository.updateSubscription(orgId, username, eventType.getId(), subscriptionType, !subscribedSeverities.isEmpty(), severitiesMap);
        }
    }
}
