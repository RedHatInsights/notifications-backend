package com.redhat.cloud.notifications.routers.handlers.userconfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.Constants;
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
import com.redhat.cloud.notifications.oapi.OApiFilter;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import com.redhat.cloud.notifications.routers.models.SettingsValueByEventTypeJsonForm;
import com.redhat.cloud.notifications.routers.models.SettingsValuesByEventType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.SubscriptionType.DRAWER;
import static com.redhat.cloud.notifications.models.SubscriptionType.INSTANT;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getUsername;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.isServiceAccountAuthentication;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

@Path(Constants.API_NOTIFICATIONS_V_1_0 + "/user-config")
public class UserConfigResource {

    private static final ObjectMapper mapper = new ObjectMapper();

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

    @POST
    @Path("/notification-event-type-preference")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Tag(name = OApiFilter.PRIVATE)
    @Transactional
    public Response saveSettingsByEventType(@Context SecurityContext sec, @NotNull @Valid SettingsValuesByEventType userSettings) {
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

    private Map<Severity, Boolean> buildAllSeveritiesUpdateDetails(boolean subscribed) {
        Map<Severity, Boolean> severitySubscriptionMap = new HashMap<>();
        for (Severity severity : Severity.values()) {
            severitySubscriptionMap.put(severity, subscribed);
        }
        return severitySubscriptionMap;
    }

    private void handleSubscription(String bundleName, String applicationName, SettingsValuesByEventType.EventTypeSettingsValue eventTypeValue, Optional<EventType> eventType, String orgId, String userName) {

        // If 'subscriptionTypes' is empty, build it from 'emailSubscriptionTypes'
        if (eventTypeValue.subscriptionTypes == null || eventTypeValue.subscriptionTypes.isEmpty()) {
            eventTypeValue.subscriptionTypes = new HashMap<>();
            eventTypeValue.emailSubscriptionTypes.forEach((key, value) ->
                eventTypeValue.subscriptionTypes.put(key, buildAllSeveritiesUpdateDetails(value)));
        }

        eventTypeValue.subscriptionTypes.forEach((subscriptionType, subscriptionTypeDetails) -> {
            boolean supported = isTemplateSupported(bundleName, applicationName, eventType.get(), subscriptionType, orgId);

            if (!supported) {
                throw new NotFoundException(String.format("Event type '%s' doesn't support '%s' subscription", eventType.get().getId(), subscriptionType.name()));
            }

            boolean subscribed = subscriptionTypeDetails.entrySet().stream().anyMatch(Map.Entry::getValue);

            subscriptionRepository.updateSubscription(orgId, userName, eventType.get().getId(), subscriptionType, subscribed, subscriptionTypeDetails);
        });
    }

    @GET
    @Path("/notification-event-type-preference")
    @Produces(APPLICATION_JSON)
    @Tag(name = OApiFilter.PRIVATE)
    public Response getSettingsSchemaByEventType(@Context SecurityContext sec) {
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

    @GET
    @Path("/notification-event-type-preference/{bundleName}/{applicationName}")
    @Produces(APPLICATION_JSON)
    @Tag(name = OApiFilter.PRIVATE)
    public Response getPreferencesByEventType(
            @Context SecurityContext sec, @RestPath String bundleName, @RestPath String applicationName) {
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

        List<Application> applicationsWithForcedEmails = applicationRepository.getApplicationsWithForcedEmail(bundleRepository.getBundle(bundleName).getId(), orgId);

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
                            eventTypeSettingsValue.subscriptionTypes.put(subscriptionType, buildAllSeveritiesUpdateDetails(subscribedByDefault));
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
            if (!backendConfig.isDrawerEnabled() && subscriptionType == DRAWER) {
                supported = false;
            } else {
                boolean canUseTemplateBetaVersion = backendConfig.isUseBetaTemplatesEnabled(orgId);
                TemplateDefinition templateDefinition = getTemplateDefinition(bundleName, applicationName, eventType.getName(), subscriptionType, canUseTemplateBetaVersion);
                supported = templateService.isValidTemplateDefinition(templateDefinition);
            }
        } else {
            supported = templateRepository.isSubscriptionTypeSupported(eventType.getId(), subscriptionType);
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

    private static void forbidAccessInCaseOfServiceAccountAuthentication(SecurityContext sec) {
        if (isServiceAccountAuthentication(sec)) {
            throw new ForbiddenException("This api can't be used with a service account authentication");
        }
    }
}
