package com.redhat.cloud.notifications.routers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.auth.RhIdPrincipal;
import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.BundleResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.models.EmailSubscription.EmailSubscriptionType;
import com.redhat.cloud.notifications.routers.models.SettingsValueJsonForm;
import com.redhat.cloud.notifications.routers.models.SettingsValues;
import com.redhat.cloud.notifications.routers.models.SettingsValues.ApplicationSettingsValue;
import com.redhat.cloud.notifications.routers.models.SettingsValues.BundleSettingsValue;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
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
    @Path("/email-preference")
    @Operation(hidden = true)
    public Uni<Response> saveSettings(@Context SecurityContext sec, @Valid SettingsValues values) {

        final RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        final String account = principal.getAccount();
        final String name = principal.getName();

        final List<Multi<Boolean>> subscriptionRequests = new ArrayList<>();

        values.bundles.forEach((bundle, bundleSettingsValue) ->
                bundleSettingsValue.applications.forEach((application, applicationSettingsValue) ->
                applicationSettingsValue.notifications.forEach((emailSubscriptionType, value) -> {
                    if (value) {
                        subscriptionRequests.add(emailSubscriptionResources.subscribe(
                                account, name, bundle, application, emailSubscriptionType
                        ).toMulti());
                    } else {
                        subscriptionRequests.add(emailSubscriptionResources.unsubscribe(
                                account, name, bundle, application, emailSubscriptionType
                        ).toMulti());
                    }
                })));

        /*
         * (Un)subscriptions will be run sequentially because of the concatenation.
         * This is mandatory because the Hibernate session must not be used concurrently.
         */
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
    @Path("/email-preference")
    @Operation(hidden = true)
    public Uni<Response> getSettingsSchema(@Context SecurityContext sec) {

        final RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        final String account = principal.getAccount();
        final String name = principal.getName();

        Uni<SettingsValues> settingsValuesUni = getSettingsValueForUser(account, name);

        return settingsValuesToJsonForm(settingsValuesUni).onItem().transform(jsonFormString -> {
            Response.ResponseBuilder builder;
            builder = Response.ok(jsonFormString);
            EntityTag etag = new EntityTag(String.valueOf(jsonFormString.hashCode()));
            builder.header("ETag", etag);
            return builder.build();
        });
    }

    private Uni<SettingsValues> getSettingsValueForUser(String account, String username) {
        return Uni.createFrom().deferred(() -> {
            final SettingsValues values = new SettingsValues();

            return bundleResources.getBundles()
                    .onItem().transformToMulti(bundle -> {
                        BundleSettingsValue bundleSettingsValue = new BundleSettingsValue();
                        bundleSettingsValue.name = bundle.getDisplay_name();
                        values.bundles.put(bundle.getName(), bundleSettingsValue);
                        return applicationResources.getApplications(bundle.getName())
                                .onItem().transformToMulti(application -> {
                                    ApplicationSettingsValue applicationSettingsValue = new ApplicationSettingsValue();
                                    applicationSettingsValue.name = application.getDisplay_name();
                                    values.bundles.get(bundle.getName()).applications.put(application.getName(), applicationSettingsValue);
                                    return Multi.createFrom().items(EmailSubscriptionType.values())
                                            .onItem().transformToMulti(emailSubscriptionType -> {
                                                values.bundles.get(bundle.getName()).applications.get(application.getName()).notifications.put(emailSubscriptionType, false);
                                                return Multi.createFrom().empty();
                                            }).concatenate();
                                }).concatenate();
                    }).concatenate().collectItems().asList().onItem().transformToMulti(objects -> emailSubscriptionResources.getEmailSubscriptionsForUser(account, username))
                    .onItem().transform(emailSubscription -> {
                        values.bundles.get(emailSubscription.getBundle()).applications.get(emailSubscription.getApplication()).notifications.put(emailSubscription.getType(), true);
                        return emailSubscription;
                    }).collectItems().asList().onItem().transform(emailSubscriptions -> values);
        });
    }

    private Uni<String> settingsValuesToJsonForm(Uni<SettingsValues> settingsValuesUni) {
        return settingsValuesUni.onItem().transform(settingsValues -> SettingsValueJsonForm.fromSettingsValue(settingsValues))
                .onItem().transform(settingsValueJsonForms -> {
                    try {
                        return mapper.writeValueAsString(settingsValueJsonForms);
                    } catch (JsonProcessingException jpe) {
                        throw new IllegalArgumentException(
                                String.format("Unable to convert '%s' to String", settingsValueJsonForms),
                                jpe
                        );
                    }
                });
    }

}
