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
     * @param payloadDetailsId the identifier of the payload's details record.
     */
    @Transactional
    public void deleteById(final UUID payloadDetailsId) {
        final String deletePayloadById =
            "DELETE FROM " +
                "PayloadDetails " +
            "WHERE " +
                "id = :id";

        this.entityManager
            .createQuery(deletePayloadById)
            .setParameter("id", payloadDetailsId)
            .executeUpdate();
    }

    /**
     * Fetches the payload from the database by its identifier.
     * @param payloadDetailsId the payload's identifier to fetch the contents
     *                         for.
     *
     * @return the payload for the given event.
     */
    @Transactional
    public Optional<PayloadDetails> findById(final UUID payloadDetailsId) {
        final String findPayloadDetailsById =
            "FROM " +
                "PayloadDetails " +
            "WHERE " +
                "id = :id";

        try {
            return Optional.of(
                this.entityManager
                    .createQuery(findPayloadDetailsById, PayloadDetails.class)
                    .setParameter("id", payloadDetailsId)
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
