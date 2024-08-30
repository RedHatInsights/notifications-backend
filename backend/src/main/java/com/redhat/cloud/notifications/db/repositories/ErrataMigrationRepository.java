package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import com.redhat.cloud.notifications.routers.internal.errata.ErrataSubscription;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;

import java.util.List;

@ApplicationScoped
public class ErrataMigrationRepository {
    /**
     * The internal name of the Errata application in our database.
     */
    public static final String ERRATA_APPLICATION_NAME = "errata-notifications";

    @Inject
    StatelessSession statelessSession;

    /**
     * Finds all the Errata event types in the database.
     * @return the list of Errata event types in the database.
     */
    public List<EventType> findErrataEventTypes() {
        final String query =
            "FROM " +
                "EventType AS et " +
            "INNER JOIN " +
                "et.application AS applications " +
            "WHERE " +
                "applications.name = :applicationName";

        return this.statelessSession
            .createQuery(query, EventType.class)
            .setParameter("applicationName", ERRATA_APPLICATION_NAME)
            .getResultList();
    }

    /**
     * Saves the given Errata subscriptions in the database.
     * @param errataSubscriptions the list of the Errata subscriptions to save
     *                            in the database.
     */
    public void saveErrataSubscriptions(final List<ErrataSubscription> errataSubscriptions) {
        final Transaction transaction = this.statelessSession.beginTransaction();
        transaction.begin();

        final String insertSql =
            "INSERT INTO " +
                "email_subscriptions(user_id, org_id, event_type_id, subscription_type, subscribed) " +
            "VALUES " +
                "(:userId, :orgId, :eventTypeId, 'INSTANT', true) " +
            "ON CONFLICT DO NOTHING";

        try {
            long totalInsertionCount = 0;
            for (final ErrataSubscription errataSubscription : errataSubscriptions) {
                // For each errata subscription we need to insert subscriptions for
                // every event type in our database.
                for (final EventType errataEventType : errataSubscription.eventTypeSubscriptions()) {
                    try {
                        this.statelessSession
                            .createNativeQuery(insertSql, EventTypeEmailSubscription.class)
                            .setParameter("userId", errataSubscription.username())
                            .setParameter("orgId", errataSubscription.org_id())
                            .setParameter("eventTypeId", errataEventType.getId())
                            .executeUpdate();
                    } catch (final ConstraintViolationException e) {
                        Log.errorf("[org_id: %s][username: %s][event_type_id: %s][event_type_name: %s] Unable to persist errata subscription due to a database constraint violation", e, errataSubscription.org_id(), errataSubscription.username(), errataEventType.getId(), errataEventType.getName());
                        continue;
                    }

                    Log.infof("[org_id: %s][username: %s][event_type_id: %s][event_type_name: %s] Persisted errata subscription", errataSubscription.org_id(), errataSubscription.username(), errataEventType.getId(), errataEventType.getName());

                    totalInsertionCount++;
                }
            }

            transaction.commit();
            Log.infof("Persisted %s errata subscriptions from a total number of %s scanned subscriptions from the file.", totalInsertionCount, errataSubscriptions.size());
        } catch (final Exception e) {
            transaction.rollback();
            Log.error("The insertions of the Errata subscriptions were rolled back due to an exception", e);
        }
    }
}
