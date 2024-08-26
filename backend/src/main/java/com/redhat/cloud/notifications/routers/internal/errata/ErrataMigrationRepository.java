package com.redhat.cloud.notifications.routers.internal.errata;

import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscriptionId;
import com.redhat.cloud.notifications.models.SubscriptionType;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.hibernate.resource.transaction.spi.TransactionStatus;

import java.util.List;

@ApplicationScoped
public class ErrataMigrationRepository {
    /**
     * The maximum size of the batch of subscriptions we want to insert in the
     * database.
     */
    private static final int INSERTION_BATCH_SIZE = 500;

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
    protected List<EventType> findErrataEventTypes() {
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
    protected void saveErrataSubscriptions(final List<ErrataSubscription> errataSubscriptions) {
        Log.infof("Errata subscriptions' migration begins with a batch size of %s", INSERTION_BATCH_SIZE);

        final List<EventType> errataEventTypes = this.findErrataEventTypes();

        final Transaction transaction = this.statelessSession.beginTransaction();
        transaction.begin();

        try {
            long totalInsertionCount = 0;
            for (final ErrataSubscription errataSubscription : errataSubscriptions) {
                // For each errata subscription we need to insert subscriptions for
                // every event type in our database.
                for (final EventType errataEventType : errataEventTypes) {
                    final EventTypeEmailSubscriptionId id = new EventTypeEmailSubscriptionId(
                        errataSubscription.org_id(),
                        errataSubscription.username(),
                        errataEventType.getId(),
                        SubscriptionType.INSTANT
                    );

                    final EventTypeEmailSubscription eventTypeEmailSubscription = new EventTypeEmailSubscription();
                    eventTypeEmailSubscription.setId(id);
                    eventTypeEmailSubscription.setEventType(errataEventType);
                    eventTypeEmailSubscription.setSubscribed(true);

                    final TransactionStatus status = transaction.getStatus();
                    try {
                        this.statelessSession.insert(eventTypeEmailSubscription);
                    } catch (final ConstraintViolationException e) {
                        Log.errorf("[org_id: %s][username: %s][event_type_id: %s][event_type_name: %s] Unable to persist errata subscription due to a database constraint violation", e, errataSubscription.org_id(), errataSubscription.username(), errataEventType.getId(), errataEventType.getName());
                        continue;
                    }
                    final TransactionStatus status2 = transaction.getStatus();

                    Log.infof("[org_id: %s][username: %s][event_type_id: %s][event_type_name: %s] Persisted errata subscription", errataSubscription.org_id(), errataSubscription.username(), errataEventType.getId(), errataEventType.getName());

                    totalInsertionCount++;
                }
            }

            transaction.commit();
            Log.infof("Persisted %s errata subscriptions from a total number of %s scanned subscriptions from the file. For each errata subscription %s subscriptions were persisted in Notifications", totalInsertionCount, errataSubscriptions.size(), errataEventTypes.size());
        } catch (final Exception e) {
            transaction.rollback();
            Log.error("The insertions of the Errata subscriptions were rolled back due to an exception", e);
        }
    }
}
