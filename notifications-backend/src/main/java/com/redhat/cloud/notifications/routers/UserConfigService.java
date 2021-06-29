package com.redhat.cloud.notifications.routers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.BundleResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.routers.models.SettingsValueJsonForm;
import com.redhat.cloud.notifications.routers.models.SettingsValues;
import com.redhat.cloud.notifications.routers.models.SettingsValues.ApplicationSettingsValue;
import com.redhat.cloud.notifications.routers.models.SettingsValues.BundleSettingsValue;
import com.redhat.cloud.notifications.routers.models.UserConfigPreferences;
import com.redhat.cloud.notifications.templates.EmailTemplate;
import com.redhat.cloud.notifications.templates.EmailTemplateFactory;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;

import javax.inject.Inject;
import javax.validation.Valid;
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

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Path(Constants.API_NOTIFICATIONS_V_1_0 + "/user-config")
public class UserConfigService {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Inject
    EndpointEmailSubscriptionResources emailSubscriptionResources;

    @Inject
    BundleResources bundleResources;

    @Inject
    ApplicationResources applicationResources;

    @Inject
    EmailTemplateFactory emailTemplateFactory;

    @POST
    @Path("/notification-preference")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Operation(hidden = true)
    public Uni<Response> saveSettings(@Context SecurityContext sec, @Valid SettingsValues values) {

        final RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        final String account = principal.getAccount();
        final String name = principal.getName();

        final List<Multi<Boolean>> subscriptionRequests = new ArrayList<>();

        values.bundles.forEach((bundleName, bundleSettingsValue) ->
                bundleSettingsValue.applications.forEach((applicationName, applicationSettingsValue) ->
                applicationSettingsValue.notifications.forEach((emailSubscriptionType, subscribed) -> {
                    if (subscribed) {
                        subscriptionRequests.add(
                                applicationResources.getApplication(bundleName, applicationName)
                                .onItem().ifNotNull().transformToUni(application -> {
                                    return emailSubscriptionResources.subscribe(
                                            account, name, bundleName, applicationName, emailSubscriptionType
                                    );
                                }).toMulti()
                        );

                    } else {
                        subscriptionRequests.add(emailSubscriptionResources.unsubscribe(
                                account, name, bundleName, applicationName, emailSubscriptionType
                        ).toMulti());
                    }
                })));

        return Multi.createBy().concatenating().streams(subscriptionRequests)
                .collect().asList()
                .onItem().transform(subscriptionResults -> {
                    boolean allisSuccess = subscriptionResults.stream().allMatch(Boolean.TRUE::equals);
                    Response.ResponseBuilder builder;
                    if (allisSuccess) {
                        builder = Response.ok();
                    } else {
                        // Prevent from saving
                        builder = Response.serverError().entity("Storing of settings Failed.");
                        builder.type("text/plain");
                    }
                    return builder.build();
                });
    }

    @GET
    @Path("/notification-preference/{bundleName}/{applicationName}")
    @Produces(APPLICATION_JSON)
    @Operation(hidden = true)
    public Uni<UserConfigPreferences> getPreferences(
            @Context SecurityContext sec,
            @PathParam("bundleName") String bundleName,
            @PathParam("applicationName") String applicationName) {
        final RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        final String account = principal.getAccount();
        final String name = principal.getName();

        final UserConfigPreferences preferences = new UserConfigPreferences();
        // TODO Get the DAILY and INSTANT subscriptions with a single SQL query and return UserConfigPreferences directly from Hibernate.
        return emailSubscriptionResources.getEmailSubscription(account, name, bundleName, applicationName, EmailSubscriptionType.DAILY)
                .onItem().invoke(daily -> preferences.setDailyEmail(daily != null))
                .onItem().transformToUni(_ignored -> emailSubscriptionResources.getEmailSubscription(account, name, bundleName, applicationName, EmailSubscriptionType.INSTANT))
                .onItem().invoke(instant -> preferences.setInstantEmail(instant != null))
                .replaceWith(preferences);
    }

    @GET
    @Path("/notification-preference")
    @Produces(APPLICATION_JSON)
    @Operation(hidden = true)
    public Uni<Response> getSettingsSchema(@Context SecurityContext sec, @QueryParam("bundleName") String bundleName) {

        final RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        final String account = principal.getAccount();
        final String name = principal.getName();

        Uni<SettingsValues> settingsValuesUni = getSettingsValueForUser(account, name, bundleName);

        return settingsValuesToJsonForm(settingsValuesUni).onItem().transform(jsonFormString -> {
            Response.ResponseBuilder builder;
            builder = Response.ok(jsonFormString);
            EntityTag etag = new EntityTag(String.valueOf(jsonFormString.hashCode()));
            builder.header("ETag", etag);
            return builder.build();
        });
    }

    /**
     * Pulls the user settings values of an user across all the know applications of a bundle
     * */
    private Uni<SettingsValues> getSettingsValueForUser(String account, String username, String bundleName) {
        if (bundleName == null || bundleName.equals("")) {
            throw new BadRequestException("bundleName must have a value");
        }

        /*
         * The following stream creates a `SettingsValues` instance and then populates it by using side-effects.
         * It is done by invoking to kind of functions:
         * - database queries are executed in an asynchronous way using `.onItem().call(...)`
         * - side-effects are executed in a synchronous way using `.onItem().invoke(...)`
         */
        return Uni.createFrom().item(() -> new SettingsValues())
                .onItem().call(settingsValues ->
                        bundleResources.getBundle(bundleName)
                                .onItem().ifNull().failWith(() -> new BadRequestException("Unknown bundleName: " + bundleName))
                                .onItem().call(bundle -> {
                                    BundleSettingsValue bundleSettingsValue = new BundleSettingsValue();
                                    bundleSettingsValue.displayName = bundle.getDisplayName();
                                    settingsValues.bundles.put(bundle.getName(), bundleSettingsValue);
                                    return applicationResources.getApplications(bundle.getName())
                                            .onItem().invoke(applications -> {
                                                for (Application application : applications) {
                                                    ApplicationSettingsValue applicationSettingsValue = new ApplicationSettingsValue();
                                                    applicationSettingsValue.displayName = application.getDisplayName();
                                                    EmailTemplate applicationEmailTemplate = emailTemplateFactory.get(bundle.getName(), application.getName());
                                                    settingsValues.bundles.get(bundle.getName()).applications.put(application.getName(), applicationSettingsValue);
                                                    for (EmailSubscriptionType emailSubscriptionType : EmailSubscriptionType.values()) {
                                                        if (applicationEmailTemplate.isEmailSubscriptionSupported(emailSubscriptionType)) {
                                                            settingsValues.bundles.get(bundle.getName()).applications.get(application.getName()).notifications.put(emailSubscriptionType, false);
                                                        }
                                                    }
                                                }
                                            });
                                })
                                .onItem().call(ignoredBundle ->
                                        emailSubscriptionResources.getEmailSubscriptionsForUser(account, username)
                                                .onItem().invoke(emailSubscriptions -> {
                                                    for (EmailSubscription emailSubscription : emailSubscriptions) {
                                                        if (settingsValues.bundles.containsKey(emailSubscription.getApplication().getBundle().getName())) {
                                                            BundleSettingsValue bundleSettingsValue = settingsValues.bundles.get(emailSubscription.getApplication().getBundle().getName());
                                                            if (bundleSettingsValue.applications.containsKey(emailSubscription.getApplication().getName())) {
                                                                ApplicationSettingsValue applicationSettingsValue = bundleSettingsValue.applications.get(emailSubscription.getApplication().getName());
                                                                if (applicationSettingsValue.notifications.containsKey(emailSubscription.getType())) {
                                                                    bundleSettingsValue.applications.get(emailSubscription.getApplication().getName()).notifications.put(emailSubscription.getType(), true);
                                                                }
                                                            }
                                                        }
                                                    }
                                                })
                                )
                );
    }

    private Uni<String> settingsValuesToJsonForm(Uni<SettingsValues> settingsValuesUni) {
        return settingsValuesUni.onItem().transform(settingsValues -> SettingsValueJsonForm.fromSettingsValue(settingsValues))
                .onItem().transform(settingsValueJsonForm -> {
                    try {
                        return mapper.writeValueAsString(settingsValueJsonForm);
                    } catch (JsonProcessingException jpe) {
                        throw new IllegalArgumentException(
                                String.format("Unable to convert '%s' to String", settingsValueJsonForm),
                                jpe
                        );
                    }
                });
    }

}
