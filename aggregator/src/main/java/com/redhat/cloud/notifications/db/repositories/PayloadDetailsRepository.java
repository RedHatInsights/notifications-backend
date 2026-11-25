package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.PayloadDetails;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * The aggregator only writes the {@code payload_details} table, the engine's own repository reads it back.
 */
@ApplicationScoped
public class PayloadDetailsRepository {

    @Inject
    EntityManager entityManager;

    /**
     * Saves the payload details in the database.
     * @param payloadDetails the details to be saved in the database.
     */
    @Transactional
    public void save(final PayloadDetails payloadDetails) {
        this.entityManager.persist(payloadDetails);
    }
}
