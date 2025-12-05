package com.redhat.cloud.notifications.routers.internal.errata;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import java.util.List;
import java.util.Set;

@ApplicationScoped
@Path(Constants.API_INTERNAL + "/team-nado")
public class ErrataEmailPreferencesUserIdFixService {
    @Inject
    SubscriptionRepository subscriptionRepository;

    @Path("/migrate/rename-lowercase")
    @POST
    public void migrateMixedCaseEmailSubscriptions() {
        final Set<String> mixedCaseUserIds = this.subscriptionRepository.findMixedCaseUserIds();

        Log.debugf("Fetched the following email subscription user IDs: %s", mixedCaseUserIds);

        for (final String mixedCaseUserId : mixedCaseUserIds) {
            Log.debugf("[user_id: %s] Processing mixed case user id", mixedCaseUserId);

            final List<EventTypeEmailSubscription> mixedCaseEmailSubs = this.subscriptionRepository.findEmailSubscriptionsByUserId(mixedCaseUserId);

            Log.debugf("[user_id: %s] Fetched the following email subscriptions for user: %s", mixedCaseUserId, mixedCaseEmailSubs);

            for (final EventTypeEmailSubscription subscription : mixedCaseEmailSubs) {
                this.subscriptionRepository.setEmailSubscriptionUserIdLowercase(subscription);
            }
        }
    }
}
