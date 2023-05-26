package com.redhat.cloud.notifications.routers.internal;

import io.quarkus.logging.Log;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN;

@RolesAllowed(RBAC_INTERNAL_ADMIN)
@Path(API_INTERNAL)
public class SubscriptionToEventTypeMigrationService {

    @Inject
    EntityManager entityManager;

    @PUT
    @Path("/subscription-to-event-type/migrate")
    public void migrate() {
        Log.info("Start Events migration");
        migrateData();
        Log.info("End Events migration");
    }

    @Transactional
    void migrateData() {
        String query = "DELETE FROM EventTypeEmailSubscription";

        int affectedRows = entityManager.createQuery(query).executeUpdate();
        Log.infof("%d record(s) deleted", affectedRows);

        query = "INSERT INTO email_subscriptions (user_id, org_id, event_type_id, subscription_type, subscribed) " +
            "SELECT ees.user_id, ees.org_id, et.id, ees.subscription_type, true from endpoint_email_subscriptions ees join event_type et on ees.application_id = et.application_id";

        affectedRows = entityManager.createNativeQuery(query).executeUpdate();
        Log.infof("%d record(s) inserted", affectedRows);
    }
}
