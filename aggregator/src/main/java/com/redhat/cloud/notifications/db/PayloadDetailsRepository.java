package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.processors.payload.PayloadDetails;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PayloadDetailsRepository {

    @Inject
    EntityManager entityManager;

    /**
     * Saves the payload details in the database. Aggregator only ever writes this table -- engine's
     * own, unchanged {@code PayloadDetailsRepository} is the one that reads it back.
     */
    @Transactional
    public void save(final PayloadDetails payloadDetails) {
        this.entityManager.persist(payloadDetails);
    }
}
