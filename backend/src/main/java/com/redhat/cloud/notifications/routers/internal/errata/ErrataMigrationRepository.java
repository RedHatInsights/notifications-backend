package com.redhat.cloud.notifications.routers.internal.errata;

import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscriptionId;
import com.redhat.cloud.notifications.models.SubscriptionType;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;

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
    EntityManager entityManager;

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

        return this.entityManager
            .createQuery(query, EventType.class)
            .setParameter("applicationName", ERRATA_APPLICATION_NAME)
            .getResultList();
    }

    /**
     * Saves the given Errata subscriptions in the database.
     * @param errataSubscriptions the list of the Errata subscriptions to save
     *                            in the database.
     */
    @Transactional
    protected void saveErrataSubscriptions(final List<ErrataSubscription> errataSubscriptions) {
        Log.infof("Errata subscriptions' migration begins with a batch size of %s", INSERTION_BATCH_SIZE);

        final List<EventType> errataEventTypes = this.findErrataEventTypes();

        long totalInsertionCount = 0;
        long insertionCount = 0;
        for (final ErrataSubscription errataSubscription : errataSubscriptions) {
            // Make Hibernate send a batch query to the database to free
            // memory.
            if (insertionCount >= INSERTION_BATCH_SIZE) {
                insertionCount = 0;

                this.entityManager.flush();
                this.entityManager.clear();
            }

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

                try {
                    this.entityManager.persist(eventTypeEmailSubscription);
                } catch (final ConstraintViolationException e) {
                    Log.errorf("[org_id: %s][username: %s][event_type_id: %s][event_type_name: %s] Unable to persist errata subscription due to a database constraint violation", e, errataSubscription.org_id(), errataSubscription.username(), errataEventType.getId(), errataEventType.getName());
                }

                Log.infof("[org_id: %s][username: %s][event_type_id: %s][event_type_name: %s] Persisted errata subscription", errataSubscription.org_id(), errataSubscription.username(), errataEventType.getId(), errataEventType.getName());

                insertionCount++;
                totalInsertionCount++;
            }
        }

        Log.infof("Persisted %s errata subscriptions from a total number of %s scanned subscriptions from the file. For each errata subscription %s subscriptions were persisted in Notifications", totalInsertionCount, errataSubscriptions.size(), errataEventTypes.size());
    }
}
