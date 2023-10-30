package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.repositories.EmailSubscriptionRepository;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import com.redhat.cloud.notifications.routers.models.UserConfigPreferences;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import java.util.List;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.SubscriptionType.INSTANT;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getUsername;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class UserPreferencesForPolicy {

    @Inject
    EmailSubscriptionRepository emailSubscriptionRepository;

    @Inject
    FeatureFlipper featureFlipper;

    @Path(Constants.API_NOTIFICATIONS_V_1_0 + "/user-config")
    public static class V1 extends UserPreferencesForPolicy {

    }

    @GET
    @Path("/notification-preference/{bundleName}/{applicationName}")
    @Produces(APPLICATION_JSON)
    @Operation(hidden = true)
    @Deprecated
    public UserConfigPreferences getPreferences(
        @Context SecurityContext sec, @PathParam("bundleName") String bundleName, @PathParam("applicationName") String applicationName) {
        if (!bundleName.equals("rhel") || !applicationName.equals("policies")) {
            Log.warnf("Deprecated api '/notification-preference/{bundleName}/{applicationName}' was requested for bundle '%s' and application '%s'", bundleName, applicationName);
            throw new ForbiddenException(String.format("This api can't be used for bundle '%s' and application '%s'", bundleName, applicationName));
        }

        final String username = getUsername(sec);
        String orgId = getOrgId(sec);

        final UserConfigPreferences preferences = new UserConfigPreferences();
        preferences.setDailyEmail(false);
        preferences.setInstantEmail(false);

        List<EventTypeEmailSubscription> subscriptionTypes = emailSubscriptionRepository.getEmailSubscriptionByEventType(orgId, username, bundleName, applicationName);
        if (subscriptionTypes != null && !subscriptionTypes.isEmpty()) {
            for (EventTypeEmailSubscription subscribedEvent : subscriptionTypes) {
                if (DAILY == subscribedEvent.getType()) {
                    preferences.setDailyEmail(subscribedEvent.isSubscribed());
                } else if (featureFlipper.isInstantEmailsEnabled() && INSTANT == subscribedEvent.getType()) {
                    preferences.setInstantEmail(subscribedEvent.isSubscribed());
                }
            }
        }
        return preferences;
    }
}
