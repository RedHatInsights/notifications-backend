package com.redhat.cloud.notifications.routers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.auth.RhIdPrincipal;
import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.BundleResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.routers.models.SettingsValueJsonForm;
import com.redhat.cloud.notifications.routers.models.SettingsValues;
import com.redhat.cloud.notifications.routers.models.SettingsValues.ApplicationSettingsValue;
import com.redhat.cloud.notifications.routers.models.SettingsValues.BundleSettingsValue;
import com.redhat.cloud.notifications.routers.models.UserConfigPreferences;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;

import javax.enterprise.context.RequestScoped;
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

@Path("/api/notifications/v1.0/user-config")
@Produces("application/json")
@Consumes("application/json")
@RequestScoped
public class UserConfigService {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Inject
    EndpointEmailSubscriptionResources emailSubscriptionResources;

    @Inject
    BundleResources bundleResources;

    @Inject
    ApplicationResources applicationResources;

    @POST
    @Path("/notification-preference")
    @Operation(hidden = true)
    public Uni<Response> saveSettings(@Context SecurityContext sec, @Valid SettingsValues values) {

        final RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        final String account = principal.getAccount();
        final String name = principal.getName();

        final List<Multi<Boolean>> subscriptionRequests = new ArrayList<>();

        values.bundles.forEach((bundleName, bundleSettingsValue) ->
                bundleSettingsValue.applications.forEach((applicationName, applicationSettingsValue) ->
                applicationSettingsValue.notifications.forEach((emailSubscriptionType, value) -> {
                    if (value) {
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
                    boolean allisSuccess = subscriptionResults.stream().allMatch(isTrue -> isTrue instanceof Boolean && ((Boolean) isTrue));
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
    @Operation(hidden = true)
    public Uni<UserConfigPreferences> getPreferences(
            @Context SecurityContext sec,
            @PathParam("bundleName") String bundleName,
            @PathParam("applicationName") String applicationName) {
        final RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        final String account = principal.getAccount();
        final String name = principal.getName();

        final UserConfigPreferences preferences = new UserConfigPreferences();
        return emailSubscriptionResources.getEmailSubscription(account, name, bundleName, applicationName, EmailSubscriptionType.DAILY)
                .onItem().invoke(daily -> preferences.setDailyEmail(daily != null))
                .onItem().transformToUni(_ignored -> emailSubscriptionResources.getEmailSubscription(account, name, bundleName, applicationName, EmailSubscriptionType.INSTANT))
                .onItem().invoke(instant -> preferences.setInstantEmail(instant != null))
                .onItem().transform(_ignored -> preferences);
    }

    @GET
    @Path("/notification-preference")
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
        return Uni.createFrom().deferred(() -> {
            final SettingsValues values = new SettingsValues();

            if (bundleName == null || bundleName.equals("")) {
                throw new BadRequestException("bundleName must have a value");
            }

            return bundleResources.getBundle(bundleName)
                    .onItem().ifNull().failWith(() -> new BadRequestException("Unknown bundleName: " + bundleName))
                    .onItem().transformToMulti(bundle -> {
                        BundleSettingsValue bundleSettingsValue = new BundleSettingsValue();
                        bundleSettingsValue.displayName = bundle.getDisplayName();
                        values.bundles.put(bundle.getName(), bundleSettingsValue);
                        return applicationResources.getApplications(bundle.getName())
                                .onItem().transformToMulti(application -> {
                                    ApplicationSettingsValue applicationSettingsValue = new ApplicationSettingsValue();
                                    applicationSettingsValue.displayName = application.getDisplayName();
                                    values.bundles.get(bundle.getName()).applications.put(application.getName(), applicationSettingsValue);
                                    return Multi.createFrom().items(EmailSubscriptionType.values())
                                            .onItem().transformToMulti(emailSubscriptionType -> {
                                                values.bundles.get(bundle.getName()).applications.get(application.getName()).notifications.put(emailSubscriptionType, false);
                                                return Multi.createFrom().empty();
                                            }).concatenate();
                                }).concatenate();
                    }).collect().asList().onItem().transformToMulti(objects -> emailSubscriptionResources.getEmailSubscriptionsForUser(account, username))
                    .onItem().transform(emailSubscription -> {
                        if (values.bundles.containsKey(emailSubscription.getBundleName())) {
                            BundleSettingsValue bundleSettingsValue = values.bundles.get(emailSubscription.getBundleName());
                            if (bundleSettingsValue.applications.containsKey(emailSubscription.getApplicationName())) {
                                bundleSettingsValue.applications.get(emailSubscription.getApplicationName()).notifications.put(emailSubscription.getType(), true);
                            }
                        }
                        return emailSubscription;
                    }).collect().asList().onItem().transform(emailSubscriptions -> values);
        });
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
