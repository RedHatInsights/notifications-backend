package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.processors.payload.PayloadDetails;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PayloadDetailsRepository {
    @Inject
    EntityManager entityManager;

    /**
     * Deletes the payload details from the database.
     * @param eventId the identifier of the related event the payload was saved
     *                for.
     */
    @Transactional
    public void deleteById(final UUID eventId) {
        final String deletePayloadById =
            "DELETE FROM " +
                "PayloadDetails " +
            "WHERE " +
                "eventId = :event_id";

        this.entityManager
            .createQuery(deletePayloadById)
            .setParameter("event_id", eventId)
            .executeUpdate();
    }

    /**
     * Fetches the payload from the database by its event ID.
     * @param eventId the event ID to fetch the payload for.
     *
     * @return the payload for the given event.
     */
    @Transactional
    public Optional<PayloadDetails> findByEventId(final UUID eventId) {
        final String findByEventId =
            "FROM " +
                "PayloadDetails " +
            "WHERE " +
                "eventId = :event_id";

        try {
            return Optional.of(
                this.entityManager
                    .createQuery(findByEventId, PayloadDetails.class)
                    .setParameter("event_id", eventId)
                    .getSingleResult()
            );
        } catch (final NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Saves the payload details in the database.
     * @param payloadDetails the details to be saved in the database.
     */
    @Transactional
    public void save(final PayloadDetails payloadDetails) {
        this.entityManager.persist(payloadDetails);
    }
}
