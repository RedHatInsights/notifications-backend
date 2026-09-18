package com.redhat.cloud.notifications.routers.handlers.userconfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BundleRepository;
import com.redhat.cloud.notifications.db.repositories.EventTypeRepository;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
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
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import com.redhat.cloud.notifications.routers.models.SettingsValueByEventTypeJsonForm;
import com.redhat.cloud.notifications.routers.models.SettingsValuesByEventType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.SubscriptionType.DRAWER;
import static com.redhat.cloud.notifications.models.SubscriptionType.INSTANT;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getUsername;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.isServiceAccountAuthentication;

public class UserConfigResourceCommon {

    @Inject
    ObjectMapper mapper;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    BundleRepository bundleRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    EventTypeRepository eventTypeRepository;

    @Inject
    TemplateRepository templateRepository;

    @Inject
    TemplateService templateService;

    @Inject
    BackendConfig backendConfig;

    @Inject
    SubscriptionMapper subscriptionMapper;

    @Transactional
    protected Response doSaveSettingsByEventType(SecurityContext sec, SettingsValuesByEventType userSettings) {
        forbidAccessInCaseOfServiceAccountAuthentication(sec);

        final String userName = getUsername(sec);
        final String orgId = getOrgId(sec);

        // If the instant emails are disabled, we need to check that the request
        // does not contain any subscription with SubscriptionType.INSTANT.
        if (!backendConfig.isInstantEmailsEnabled() && userSettings.bundles.values().stream()
                .flatMap(bundleSettings -> bundleSettings.applications.values().stream())
                .flatMap(appSettings -> appSettings.eventTypes.values().stream())
                .flatMap(eventTypeSettings -> {
                    if (eventTypeSettings.subscriptionTypes != null &&  !eventTypeSettings.subscriptionTypes.isEmpty()) {
                        return eventTypeSettings.subscriptionTypes.keySet().stream();
                    } else {
                        return eventTypeSettings.emailSubscriptionTypes.keySet().stream();
                    }
                })
                .anyMatch(subscriptionType -> subscriptionType == INSTANT)) {
            throw new BadRequestException("Subscribing to or unsubscribing from instant emails is not supported");
        }

        // for each bundle
        userSettings.bundles.forEach((bundleName, bundleSettingsValue) ->
            // for each application
            bundleSettingsValue.applications.forEach((applicationName, applicationSettingsValue) -> {
                Application app = applicationRepository.getApplication(bundleName, applicationName);
                if (app != null) {
                    // foreach event Type
                    applicationSettingsValue.eventTypes.forEach((eventTypeName, eventTypeValue) -> {
                        Optional<EventType> eventType = eventTypeRepository.find(app.getId(), eventTypeName);
                        if (eventType.isPresent() && !eventType.get().isSubscriptionLocked()) {
                            // for each subscription
                            handleSubscription(bundleName, applicationName, eventTypeValue, eventType, orgId, userName);
                        }
                    });
                }
            }));

        return Response.ok().build();
    }

    private Map<Severity, Boolean> buildAllSeveritiesUpdateDetails(boolean subscribed, Set<Severity> availableSeverities) {
        if (availableSeverities == null || availableSeverities.isEmpty()) {
            // Legacy: no severity support → no per-severity entries
            return new HashMap<>();
        }

        Map<Severity, Boolean> severitySubscriptionMap = new HashMap<>();
        for (Severity severity : Severity.values()) {
            severitySubscriptionMap.put(
                severity,
                availableSeverities.contains(severity) ? subscribed : false
            );
        }
        return severitySubscriptionMap;
    }

    private void handleSubscription(String bundleName, String applicationName, SettingsValuesByEventType.EventTypeSettingsValue eventTypeValue, Optional<EventType> eventType, String orgId, String userName) {

        // If 'subscriptionTypes' is empty, build it from 'emailSubscriptionTypes'
        if (eventTypeValue.subscriptionTypes == null || eventTypeValue.subscriptionTypes.isEmpty()) {
            eventTypeValue.subscriptionTypes = new HashMap<>();
            eventTypeValue.emailSubscriptionTypes.forEach((key, value) ->
                eventTypeValue.subscriptionTypes.put(key, buildAllSeveritiesUpdateDetails(value, eventType.get().getAvailableSeverities())));
        }

        eventTypeValue.subscriptionTypes.forEach((subscriptionType, subscriptionTypeDetails) -> {
            boolean supported = isTemplateSupported(bundleName, applicationName, eventType.get(), subscriptionType, orgId);

            if (!supported) {
                throw new NotFoundException(String.format("Event type '%s' doesn't support '%s' subscription", eventType.get().getDisplayName(), subscriptionType.name()));
            }

            // Check it the user subscribed to available severities regarding the event type config
            Set<Severity> subscribedSeverities = subscriptionTypeDetails.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).collect(Collectors.toSet());
            for (Severity severity : subscribedSeverities) {
                if (!eventType.get().getAvailableSeverities().contains(severity)) {
                    throw new NotFoundException(String.format("Event type '%s' doesn't support '%s' severity", eventType.get().getDisplayName(), severity.name()));
                }
            }

            boolean subscribed;
            // if the event type don't support severities, then read the subscription status from the legacy structure
            if (!eventType.get().getAvailableSeverities().isEmpty()) {
                subscribed = !subscribedSeverities.isEmpty();
            } else if (eventTypeValue.emailSubscriptionTypes != null && eventTypeValue.emailSubscriptionTypes.containsKey(subscriptionType)) {
                subscribed = eventTypeValue.emailSubscriptionTypes.get(subscriptionType);
            } else {
                subscribed = false;
            }

            subscriptionRepository.updateSubscription(orgId, userName, eventType.get().getId(), subscriptionType, subscribed, subscriptionTypeDetails);
        });
    }


    protected Response getSettingsSchemaByEventType(SecurityContext sec) {
        forbidAccessInCaseOfServiceAccountAuthentication(sec);

        final String name = getUsername(sec);
        String orgId = getOrgId(sec);

        List<EventTypeEmailSubscription> emailSubscriptions = subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(orgId, name);
        SettingsValuesByEventType settingsValues = getSettingsValueForUserByEventType(emailSubscriptions, orgId);
        String jsonFormString = settingsValuesToJsonForm(settingsValues);
        Response.ResponseBuilder builder = Response.ok(jsonFormString);
        EntityTag etag = new EntityTag(String.valueOf(jsonFormString.hashCode()));
        builder.header("ETag", etag);
        return builder.build();
    }

    protected Response getPreferencesByEventType(
            SecurityContext sec, String bundleName, String applicationName) {
        forbidAccessInCaseOfServiceAccountAuthentication(sec);

        final String name = getUsername(sec);
        String orgId = getOrgId(sec);

        SettingsValuesByEventType settingsValues = getSettingsValueForUserByEventType(orgId, name, bundleName, applicationName);
        String jsonFormString = settingsValuesToJsonForm(settingsValues, bundleName, applicationName);
        Response.ResponseBuilder builder = Response.ok(jsonFormString);
        EntityTag etag = new EntityTag(String.valueOf(jsonFormString.hashCode()));
        builder.header("ETag", etag);
        return builder.build();
    }

    private String settingsValuesToJsonForm(SettingsValuesByEventType settingsValues, String bundleName, String applicationName) {
        final SettingsValueByEventTypeJsonForm.Application settingsValueJsonForm = SettingsValueByEventTypeJsonForm.fromSettingsValueEventTypes(settingsValues, bundleName, applicationName);
        try {
            return mapper.writeValueAsString(settingsValueJsonForm);
        } catch (JsonProcessingException jpe) {
            throw new IllegalArgumentException(
                String.format("Unable to convert '%s' to String", settingsValueJsonForm),
                jpe
            );
        }
    }

    private String settingsValuesToJsonForm(SettingsValuesByEventType settingsValues) {
        SettingsValueByEventTypeJsonForm settingsValueJsonForm = SettingsValueByEventTypeJsonForm.fromSettingsValue(settingsValues);
        try {
            return mapper.writeValueAsString(settingsValueJsonForm);
        } catch (JsonProcessingException jpe) {
            throw new IllegalArgumentException(
                String.format("Unable to convert '%s' to String", settingsValueJsonForm),
                jpe
            );
        }
    }


    private SettingsValuesByEventType getSettingsValueForUserByEventType(String orgId, String username, String bundleName, String applicationName) {
        List<EventTypeEmailSubscription> eventTypeEmailSubscriptions = subscriptionRepository.getEmailSubscriptionByEventType(orgId, username, bundleName, applicationName);

        Bundle bundle = bundleRepository.getBundle(bundleName);
        if (bundle == null) {
            throw new NotFoundException(String.format("No bundle named '%s' found", bundleName));
        }
        List<Application> applicationsWithForcedEmails = applicationRepository.getApplicationsWithForcedEmail(bundle.getId(), orgId);

        SettingsValuesByEventType settingsValues = new SettingsValuesByEventType();
        Application application = applicationRepository.getApplication(bundleName, applicationName);
        List<String> mapApplicationsWithForcedEmail = applicationsWithForcedEmails.stream().map(app -> app.getName()).collect(Collectors.toList());
        addApplicationStructureDetails(settingsValues, application, mapApplicationsWithForcedEmail.contains(applicationName), orgId);

        patchWithUserPreferencesIfExists(settingsValues, eventTypeEmailSubscriptions);
        return settingsValues;
    }

    private void addApplicationStructureDetails(final SettingsValuesByEventType settingsValues, Application application, boolean withForcedEmails, final String orgId) {
        Bundle bundle = application.getBundle();
        SettingsValuesByEventType.ApplicationSettingsValue applicationSettingsValue = new SettingsValuesByEventType.ApplicationSettingsValue();
        applicationSettingsValue.displayName = application.getDisplayName();
        boolean showHiddenEventTypes = backendConfig.isShowHiddenEventTypes(orgId);
        for (EventType eventType : application.getEventTypes()) {
            if (eventType.isVisible() || showHiddenEventTypes) {
                SettingsValuesByEventType.EventTypeSettingsValue eventTypeSettingsValue = new SettingsValuesByEventType.EventTypeSettingsValue();
                eventTypeSettingsValue.displayName = eventType.getDisplayName();
                eventTypeSettingsValue.hasForcedEmail = withForcedEmails;
                eventTypeSettingsValue.subscriptionLocked = eventType.isSubscriptionLocked();
                eventTypeSettingsValue.availableSeverities = eventType.getAvailableSeverities();
                for (SubscriptionType subscriptionType : SubscriptionType.values()) {
                    if (backendConfig.isInstantEmailsEnabled() || subscriptionType != INSTANT) {
                        boolean supported = isTemplateSupported(bundle.getName(), application.getName(), eventType, subscriptionType, orgId);

                        if (supported) {
                            boolean subscribedByDefault = subscriptionType.isSubscribedByDefault() || eventType.isSubscribedByDefault();
                            eventTypeSettingsValue.emailSubscriptionTypes.put(subscriptionType, subscribedByDefault);
                            eventTypeSettingsValue.subscriptionTypes.put(subscriptionType, buildAllSeveritiesUpdateDetails(subscribedByDefault, eventType.getAvailableSeverities()));
                        }
                    }
                }
                if (!eventTypeSettingsValue.subscriptionTypes.isEmpty()) {
                    applicationSettingsValue.eventTypes.put(eventType.getName(), eventTypeSettingsValue);
                }
            }
        }

        if (!applicationSettingsValue.eventTypes.isEmpty()) {
            settingsValues.bundles.computeIfAbsent(bundle.getName(), unused -> {
                SettingsValuesByEventType.BundleSettingsValue bundleSettingsValue = new SettingsValuesByEventType.BundleSettingsValue();
                bundleSettingsValue.displayName = bundle.getDisplayName();
                return bundleSettingsValue;
            }).applications.put(application.getName(), applicationSettingsValue);
        }
    }

    private boolean isTemplateSupported(String bundleName, String applicationName, EventType eventType, SubscriptionType subscriptionType, final String orgId) {
        boolean supported;
        if (backendConfig.isUseCommonTemplateModuleForUserPrefApisToggle()) {
            if (!backendConfig.isDrawerEnabled(orgId) && subscriptionType == DRAWER) {
                supported = false;
            } else if (subscriptionType == DRAWER && !eventType.isIncludedInDrawer()) {
                supported = false;
            } else {
                boolean canUseTemplateBetaVersion = backendConfig.isUseBetaTemplatesEnabled(orgId);
                TemplateDefinition templateDefinition = getTemplateDefinition(bundleName, applicationName, eventType.getName(), subscriptionType, canUseTemplateBetaVersion);
                supported = templateService.isValidTemplateDefinition(templateDefinition);
            }
        } else {
            supported = templateRepository.isSubscriptionTypeSupported(eventType.getId(), subscriptionType, orgId);
        }
        return supported;
    }

    private static TemplateDefinition getTemplateDefinition(final String bundleName, final String applicationName, final String eventTypeName, final SubscriptionType subscriptionType, final boolean canUseBetaVersion) {
        IntegrationType integrationType = null;
        if (subscriptionType == INSTANT) {
            integrationType = IntegrationType.EMAIL_BODY;
        } else if (subscriptionType == DAILY) {
            integrationType = IntegrationType.EMAIL_DAILY_DIGEST_BODY;
        } else if (subscriptionType == DRAWER) {
            integrationType = IntegrationType.DRAWER;
        }
        return new TemplateDefinition(
            integrationType,
            bundleName,
            applicationName,
            eventTypeName,
            canUseBetaVersion
        );
    }

    private void patchWithUserPreferencesIfExists(final SettingsValuesByEventType settingsValues, List<EventTypeEmailSubscription> emailSubscriptions) {
        for (EventTypeEmailSubscription emailSubscription : emailSubscriptions) {
            SettingsValuesByEventType.BundleSettingsValue bundleSettings = settingsValues.bundles.get(emailSubscription.getEventType().getApplication().getBundle().getName());
            if (bundleSettings != null) {
                SettingsValuesByEventType.ApplicationSettingsValue appSettings = bundleSettings.applications.get(emailSubscription.getEventType().getApplication().getName());
                if (appSettings != null) {
                    SettingsValuesByEventType.EventTypeSettingsValue eventTypeSettings = appSettings.eventTypes.get(emailSubscription.getEventType().getName());

                    if (eventTypeSettings != null) {
                        if (eventTypeSettings.emailSubscriptionTypes.containsKey(emailSubscription.getType())) {
                            eventTypeSettings.emailSubscriptionTypes.put(emailSubscription.getType(), emailSubscription.isSubscribed());
                        }

                        if (eventTypeSettings.subscriptionTypes.containsKey(emailSubscription.getType())) {
                            eventTypeSettings.subscriptionTypes.put(emailSubscription.getType(), emailSubscription.getSeverities());
                        }
                    }
                }
            }
        }
    }

    private SettingsValuesByEventType getSettingsValueForUserByEventType(List<EventTypeEmailSubscription> emailSubscriptions, String orgId) {
        SettingsValuesByEventType settingsValues = new SettingsValuesByEventType();

        for (Bundle bundle : bundleRepository.getBundles()) {
            List<String> applicationsWithForcedEmails = applicationRepository.getApplicationsWithForcedEmail(bundle.getId(), orgId)
                    .stream().map(Application::getName).collect(Collectors.toList());
            for (Application application : bundle.getApplications()) {
                addApplicationStructureDetails(settingsValues, application, applicationsWithForcedEmails.contains(application.getName()), orgId);
            }
        }

        patchWithUserPreferencesIfExists(settingsValues, emailSubscriptions);
        return settingsValues;
    }

    protected List<BundleSubscriptionDTO> getSubscriptions(
            SecurityContext sec,
            String bundleName,
            String applicationName,
            String eventTypeName
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

    protected void doUpdateSubscriptions(
            SecurityContext sec,
            List<BundleSubscriptionUpdateDTO> body
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
