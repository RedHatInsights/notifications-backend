package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.processors.payload.PayloadDetails;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PayloadDetailsRepository {
    @Inject
    EntityManager entityManager;

    /**
     * Fetches the payload from the database by its identifier.
     * @param payloadDetailsId the payload's identifier to fetch the contents
     *                         for.
     *
     * @return the payload for the given event.
     */
    public Optional<PayloadDetails> findById(final UUID payloadDetailsId) {
        return Optional.ofNullable(
            this.entityManager.find(PayloadDetails.class, payloadDetailsId)
        );
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
