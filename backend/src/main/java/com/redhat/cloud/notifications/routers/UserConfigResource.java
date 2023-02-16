package com.redhat.cloud.notifications.routers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BundleRepository;
import com.redhat.cloud.notifications.db.repositories.EmailSubscriptionRepository;
import com.redhat.cloud.notifications.db.repositories.EventTypeRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import com.redhat.cloud.notifications.routers.models.SettingsValueByEventTypeJsonForm;
import com.redhat.cloud.notifications.routers.models.SettingsValueJsonForm;
import com.redhat.cloud.notifications.routers.models.SettingsValues;
import com.redhat.cloud.notifications.routers.models.SettingsValues.ApplicationSettingsValue;
import com.redhat.cloud.notifications.routers.models.SettingsValues.BundleSettingsValue;
import com.redhat.cloud.notifications.routers.models.SettingsValuesByEventType;
import com.redhat.cloud.notifications.routers.models.UserConfigPreferences;
import com.redhat.cloud.notifications.templates.TemplateEngineClient;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getAccountId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Path(Constants.API_NOTIFICATIONS_V_1_0 + "/user-config")
public class UserConfigResource {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Inject
    EmailSubscriptionRepository emailSubscriptionRepository;

    @Inject
    BundleRepository bundleRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    @RestClient
    TemplateEngineClient templateEngineClient;

    @Inject
    EventTypeRepository eventTypeRepository;

    @POST
    @Path("/notification-preference")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Transactional
    public Response saveSettings(@Context SecurityContext sec, @NotNull @Valid SettingsValues values) {

        final String name = getUserName(sec);
        final String accountId = getAccountId(sec);
        final String orgId = getOrgId(sec);

        final List<Boolean> subscriptionRequests = new ArrayList<>();

        values.bundles.forEach((bundleName, bundleSettingsValue) ->
                bundleSettingsValue.applications.forEach((applicationName, applicationSettingsValue) -> {
                    Application app = applicationRepository.getApplication(bundleName, applicationName);
                    applicationSettingsValue.notifications.forEach((emailSubscriptionType, subscribed) -> {
                        if (subscribed) {
                            if (app != null) {
                                subscriptionRequests.add(emailSubscriptionRepository.subscribe(
                                        accountId, orgId, name, bundleName, applicationName, emailSubscriptionType
                                ));
                            }
                        } else {
                            subscriptionRequests.add(emailSubscriptionRepository.unsubscribe(
                                    orgId, name, bundleName, applicationName, emailSubscriptionType
                            ));
                        }
                    });
                }));

        return computeSaveSettingResponse(subscriptionRequests);
    }

    private static Response computeSaveSettingResponse(List<Boolean> subscriptionRequests) {
        boolean allisSuccess = subscriptionRequests.stream().allMatch(Boolean.TRUE::equals);
        Response.ResponseBuilder builder;
        if (allisSuccess) {
            builder = Response.ok();
        } else {
            // Prevent from saving
            builder = Response.serverError().entity("Storing of settings Failed.");
            builder.type("text/plain");
        }
        return builder.build();
    }

    private static String getUserName(SecurityContext sec) {
        final String userName = ((RhIdPrincipal) sec.getUserPrincipal()).getName();
        return userName;
    }

    @GET
    @Path("/notification-preference/{bundleName}/{applicationName}")
    @Produces(APPLICATION_JSON)
    public UserConfigPreferences getPreferences(
            @Context SecurityContext sec,
            @PathParam("bundleName") String bundleName,
            @PathParam("applicationName") String applicationName) {
        final RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        final String name = principal.getName();
        String orgId = getOrgId(sec);

        final UserConfigPreferences preferences = new UserConfigPreferences();
        // TODO Get the DAILY and INSTANT subscriptions with a single SQL query and return UserConfigPreferences directly from Hibernate.
        EmailSubscription daily = emailSubscriptionRepository.getEmailSubscription(orgId, name, bundleName, applicationName, EmailSubscriptionType.DAILY);
        preferences.setDailyEmail(daily != null);
        EmailSubscription instant = emailSubscriptionRepository.getEmailSubscription(orgId, name, bundleName, applicationName, EmailSubscriptionType.INSTANT);
        preferences.setInstantEmail(instant != null);
        return preferences;
    }

    @GET
    @Path("/notification-preference")
    @Produces(APPLICATION_JSON)
    public Response getSettingsSchema(@Context SecurityContext sec, @QueryParam("bundleName") String bundleName) {

        final RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        final String name = principal.getName();
        String orgId = getOrgId(sec);

        SettingsValues settingsValues = getSettingsValueForUser(orgId, name, bundleName);

        String jsonFormString = settingsValuesToJsonForm(settingsValues);
        Response.ResponseBuilder builder;
        builder = Response.ok(jsonFormString);
        EntityTag etag = new EntityTag(String.valueOf(jsonFormString.hashCode()));
        builder.header("ETag", etag);
        return builder.build();
    }

    /**
     * Pulls the user settings values of an user across all the know applications of a bundle
     */
    private SettingsValues getSettingsValueForUser(String orgId, String username, String bundleName) {
        if (bundleName == null || bundleName.equals("")) {
            throw new BadRequestException("bundleName must have a value");
        }

        SettingsValues settingsValues = new SettingsValues();
        Bundle bundle = bundleRepository.getBundle(bundleName);
        if (bundle == null) {
            throw new BadRequestException("Unknown bundleName: " + bundleName);
        } else {
            BundleSettingsValue bundleSettingsValue = new BundleSettingsValue();
            bundleSettingsValue.displayName = bundle.getDisplayName();
            settingsValues.bundles.put(bundle.getName(), bundleSettingsValue);

            List<Application> applicationsWithForcedEmails = applicationRepository.getApplicationsWithForcedEmail(bundle.getId(), orgId);

            for (Application application : applicationRepository.getApplications(bundle.getName())) {
                ApplicationSettingsValue applicationSettingsValue = new ApplicationSettingsValue();
                applicationSettingsValue.displayName = application.getDisplayName();
                applicationSettingsValue.hasForcedEmail = applicationsWithForcedEmails.contains(application);
                for (EmailSubscriptionType emailSubscriptionType : EmailSubscriptionType.values()) {
                    // TODO NOTIF-450 How do we deal with a failure here? What kind of response should be sent to the UI when the engine is down?
                    boolean supported = templateEngineClient.isSubscriptionTypeSupported(bundle.getName(), application.getName(), emailSubscriptionType);
                    if (supported) {
                        applicationSettingsValue.notifications.put(emailSubscriptionType, false);
                    }
                }

                if (applicationSettingsValue.notifications.size() > 0) {
                    settingsValues.bundles.get(bundle.getName()).applications.put(application.getName(), applicationSettingsValue);
                }
            }
            List<EmailSubscription> emailSubscriptions = emailSubscriptionRepository.getEmailSubscriptionsForUser(orgId, username);
            for (EmailSubscription emailSubscription : emailSubscriptions) {
                if (settingsValues.bundles.containsKey(emailSubscription.getApplication().getBundle().getName())) {
                    BundleSettingsValue bundleSettings = settingsValues.bundles.get(emailSubscription.getApplication().getBundle().getName());
                    if (bundleSettings.applications.containsKey(emailSubscription.getApplication().getName())) {
                        ApplicationSettingsValue applicationSettingsValue = bundleSettings.applications.get(emailSubscription.getApplication().getName());
                        if (applicationSettingsValue.notifications.containsKey(emailSubscription.getType())) {
                            bundleSettings.applications.get(emailSubscription.getApplication().getName()).notifications.put(emailSubscription.getType(), true);
                        }
                    }
                }
            }
            return settingsValues;
        }
    }

    private String settingsValuesToJsonForm(SettingsValues settingsValues) {
        SettingsValueJsonForm settingsValueJsonForm = SettingsValueJsonForm.fromSettingsValue(settingsValues);
        try {
            return mapper.writeValueAsString(settingsValueJsonForm);
        } catch (JsonProcessingException jpe) {
            throw new IllegalArgumentException(
                String.format("Unable to convert '%s' to String", settingsValueJsonForm),
                jpe
            );
        }
    }


    @POST
    @Path("/notification-event-type-preference")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Operation(hidden = true)
    @Transactional
    public Response saveSettingsByEventType(@Context SecurityContext sec, @NotNull @Valid SettingsValuesByEventType userSettings) {

        final String userName = getUserName(sec);
        final String orgId = getOrgId(sec);

        // for each bundle
        userSettings.bundles.forEach((bundleName, bundleSettingsValue) ->
            // for each application
            bundleSettingsValue.applications.forEach((applicationName, applicationSettingsValue) -> {
                Application app = applicationRepository.getApplication(bundleName, applicationName);
                if (app != null) {
                    // foreach event Type
                    applicationSettingsValue.eventTypes.forEach((eventTypeName, eventTypeValue) -> {
                        Optional<EventType> eventType = eventTypeRepository.find(app.getId(), eventTypeName);
                        if (eventType.isPresent()) {
                            // for each email subscription
                            eventTypeValue.emailSubscriptionTypes.forEach((emailSubscriptionType, subscribed) -> {
                                if (subscribed) {
                                    emailSubscriptionRepository.subscribeEventType(
                                        orgId, userName, eventType.get().getId(), emailSubscriptionType
                                    );
                                } else {
                                    emailSubscriptionRepository.unsubscribeEventType(
                                        orgId, userName, eventType.get().getId(), emailSubscriptionType
                                    );
                                }
                            });
                        }
                    });
                }
            }));

        return Response.ok().build();
    }

    @GET
    @Path("/notification-event-type-preference")
    @Produces(APPLICATION_JSON)
    @Operation(hidden = true)
    public Response getSettingsSchemaByEventType(@Context SecurityContext sec) {
        final String name = getUserName(sec);
        String orgId = getOrgId(sec);

        List<EventTypeEmailSubscription> emailSubscriptions = emailSubscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(orgId, name);
        SettingsValuesByEventType settingsValues = getSettingsValueForUserByEventType(emailSubscriptions, orgId);
        String jsonFormString = settingsValuesToJsonForm(settingsValues);
        Response.ResponseBuilder builder;
        builder = Response.ok(jsonFormString);
        EntityTag etag = new EntityTag(String.valueOf(jsonFormString.hashCode()));
        builder.header("ETag", etag);
        return builder.build();
    }

    @GET
    @Path("/notification-event-type-preference/{bundleName}/{applicationName}")
    @Produces(APPLICATION_JSON)
    @Operation(hidden = true)
    public Response getPreferencesByEventType(
        @Context SecurityContext sec,
        @PathParam("bundleName") String bundleName,
        @PathParam("applicationName") String applicationName) {
        final String name = getUserName(sec);
        String orgId = getOrgId(sec);

        SettingsValuesByEventType settingsValues = getSettingsValueForUserByEventType(orgId, name, bundleName, applicationName);
        String jsonFormString = settingsValuesToJsonForm(settingsValues, bundleName, applicationName);
        Response.ResponseBuilder builder;
        builder = Response.ok(jsonFormString);
        EntityTag etag = new EntityTag(String.valueOf(jsonFormString.hashCode()));
        builder.header("ETag", etag);
        return builder.build();
    }


    private String settingsValuesToJsonForm(SettingsValuesByEventType settingsValues, String bundleName, String applicationName) {
        SettingsValueByEventTypeJsonForm.EventTypes settingsValueJsonForm = SettingsValueByEventTypeJsonForm.fromSettingsValueEventTypes(settingsValues, bundleName, applicationName);
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
        List<EventTypeEmailSubscription> eventTypeEmailSubscriptions = emailSubscriptionRepository.getEmailSubscriptionByEventType(orgId, username, bundleName, applicationName);

        List<Application> applicationsWithForcedEmails = applicationRepository.getApplicationsWithForcedEmail(bundleRepository.getBundle(bundleName).getId(), orgId);

        SettingsValuesByEventType settingsValues = new SettingsValuesByEventType();
        Application application = applicationRepository.getApplication(bundleName, applicationName);
        List<String> mapApplicationsWithForcedEmail = applicationsWithForcedEmails.stream().map(app -> app.getName()).collect(Collectors.toList());
        addApplicationStructureDetails(settingsValues, application, mapApplicationsWithForcedEmail.contains(applicationName));

        patchWithUserPreferencesIfExists(settingsValues, eventTypeEmailSubscriptions);
        return settingsValues;
    }

    private void addApplicationStructureDetails(final SettingsValuesByEventType settingsValues, Application application, boolean withForcedEmails) {
        Bundle bundle = application.getBundle();
        SettingsValuesByEventType.ApplicationSettingsValue applicationSettingsValue = new SettingsValuesByEventType.ApplicationSettingsValue();
        applicationSettingsValue.displayName = application.getDisplayName();
        for (EventType eventType : application.getEventTypes()) {
            SettingsValuesByEventType.EventTypeSettingsValue eventTypeSettingsValue = new SettingsValuesByEventType.EventTypeSettingsValue();
            eventTypeSettingsValue.displayName = eventType.getDisplayName();
            eventTypeSettingsValue.hasForcedEmail = withForcedEmails;
            for (EmailSubscriptionType emailSubscriptionType : EmailSubscriptionType.values()) {
                // TODO NOTIF-450 How do we deal with a failure here? What kind of response should be sent to the UI when the engine is down?
                boolean supported = templateEngineClient.isSubscriptionTypeSupported(bundle.getName(), application.getName(), emailSubscriptionType);
                if (supported) {
                    eventTypeSettingsValue.emailSubscriptionTypes.put(emailSubscriptionType, false);
                }
            }
            if (eventTypeSettingsValue.emailSubscriptionTypes.size() > 0) {
                applicationSettingsValue.eventTypes.put(eventType.getName(), eventTypeSettingsValue);
            }
        }

        if (applicationSettingsValue.eventTypes.size() > 0) {
            settingsValues.bundles.computeIfAbsent(bundle.getName(), unused -> {
                SettingsValuesByEventType.BundleSettingsValue bundleSettingsValue = new SettingsValuesByEventType.BundleSettingsValue();
                bundleSettingsValue.displayName = bundle.getDisplayName();
                return bundleSettingsValue;
            }).applications.put(application.getName(), applicationSettingsValue);
        }
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
                            eventTypeSettings.emailSubscriptionTypes.put(emailSubscription.getType(), true);
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
                addApplicationStructureDetails(settingsValues, application, applicationsWithForcedEmails.contains(application.getName()));
            }
        }

        patchWithUserPreferencesIfExists(settingsValues, emailSubscriptions);
        return settingsValues;
    }
}
