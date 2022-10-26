package com.redhat.cloud.notifications.routers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BundleRepository;
import com.redhat.cloud.notifications.db.repositories.EmailSubscriptionRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.routers.models.SettingsValueJsonForm;
import com.redhat.cloud.notifications.routers.models.SettingsValues;
import com.redhat.cloud.notifications.routers.models.SettingsValues.ApplicationSettingsValue;
import com.redhat.cloud.notifications.routers.models.SettingsValues.BundleSettingsValue;
import com.redhat.cloud.notifications.routers.models.UserConfigPreferences;
import com.redhat.cloud.notifications.templates.TemplateEngineClient;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.List;

import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getAccountId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

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

    @POST
    @Path("/notification-preference")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Operation(hidden = true)
    @Transactional
    public Response saveSettings(@Context SecurityContext sec, @NotNull @Valid SettingsValues values) {

        final RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        final String name = principal.getName();
        String accountId = getAccountId(sec);
        String orgId = getOrgId(sec);

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

    @GET
    @Path("/notification-preference/{bundleName}/{applicationName}")
    @Produces(APPLICATION_JSON)
    @Operation(hidden = true)
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
    @Operation(hidden = true)
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
     * */
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
            for (Application application : applicationRepository.getApplications(bundle.getName())) {
                ApplicationSettingsValue applicationSettingsValue = new ApplicationSettingsValue();
                applicationSettingsValue.displayName = application.getDisplayName();
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

}
