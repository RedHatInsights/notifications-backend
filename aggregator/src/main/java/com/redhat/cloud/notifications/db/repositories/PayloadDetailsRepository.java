package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.PayloadDetails;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * The aggregator only writes the {@code payload_details} table, the engine's own repository reads it back.
 */
@ApplicationScoped
public class PayloadDetailsRepository {

    @Inject
    EntityManager entityManager;

    /**
     * Saves all the payload details in one transaction: either every row is saved or none is.
     * @param payloadDetailsList the details to be saved in the database.
     */
    @Transactional
    public void saveAll(final List<PayloadDetails> payloadDetailsList) {
        payloadDetailsList.forEach(this.entityManager::persist);
    }
}
