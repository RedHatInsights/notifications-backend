package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.auth.RhIdPrincipal;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscription.EmailSubscriptionType;
import com.redhat.cloud.notifications.routers.models.SettingsValues;
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

@Path("/api/notifications/v1.0/user-config")
@Produces("application/json")
@Consumes("application/json")
@RequestScoped
public class UserConfigService {

    @Inject
    EndpointEmailSubscriptionResources emailSubscriptionResources;

    @POST
    @Path("/email-preference")
    @Operation(hidden = true)
    public Uni<Response> saveSettings(@Context SecurityContext sec, @Valid SettingsValues values) {

        final RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        final String account = principal.getAccount();
        final String name = principal.getName();

        Uni<Boolean> dailyNotification;
        Uni<Boolean> instantNotification;

        if (values.instantNotification) {
            instantNotification = emailSubscriptionResources.subscribe(account, name, EmailSubscriptionType.INSTANT);
        } else {
            instantNotification = emailSubscriptionResources.unsubscribe(account, name, EmailSubscriptionType.INSTANT);
        }
        if (values.dailyNotification) {
            dailyNotification = emailSubscriptionResources.subscribe(account, name, EmailSubscriptionType.DAILY);
        } else {
            dailyNotification = emailSubscriptionResources.unsubscribe(account, name, EmailSubscriptionType.DAILY);
        }

        return Uni.combine().all().unis(dailyNotification, instantNotification).combinedWith((daily, instant) -> {
            Response.ResponseBuilder builder;

            if (daily && instant) {
                builder = Response.ok();
            } else {
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

        Uni<EmailSubscription> dailyEmailUni = emailSubscriptionResources.getEmailSubscription(account, name, EmailSubscriptionType.DAILY);
        Uni<EmailSubscription> instantEmailUni = emailSubscriptionResources.getEmailSubscription(account, name, EmailSubscriptionType.INSTANT);

        return Uni.combine().all().unis(dailyEmailUni, instantEmailUni).combinedWith((daily, instant) -> {
            Response.ResponseBuilder builder;
            String response = settingsString;

            boolean isDaily = daily != null;
            boolean isInstant = instant != null;
            response = response.replace("%1", isInstant ? "true" : "false");
            response = response.replace("%2", isDaily ? "true" : "false");

            builder = Response.ok(response);
            EntityTag etag = new EntityTag(String.valueOf(response.hashCode()));
            builder.header("ETag", etag);
            return builder.build();
        });
    }

    private static final String settingsString =
            "[{\n" +
                    "  \"fields\": [ {\n" +
                    "    \"name\": \"instantNotification\",\n" +
                    "    \"label\": \"Instant notification\",\n" +
                    "    \"description\": \"Immediate email for each triggered application event. See notification settings for configuration.\",\n" +
                    "    \"initialValue\": %1,\n" +
                    "    \"component\": \"descriptiveCheckbox\",\n" +
                    "    \"validate\": []\n" +
                    "  }\n" +
                    "  ,\n" +
                    "  {\n" +
                    "    \"name\": \"dailyNotification\",\n" +
                    "    \"label\": \"Daily digest\",\n" +
                    "    \"description\": \"Daily summary of triggered application events in 24 hours span. See notification settings for configuration.\",\n" +
                    "    \"initialValue\": %2,\n" +
                    "    \"component\": \"descriptiveCheckbox\",\n" +
                    "    \"validate\": []\n" +
                    "\n" +
                    "  }\n" +
                    "  ]\n" +
                    "}]";
}
