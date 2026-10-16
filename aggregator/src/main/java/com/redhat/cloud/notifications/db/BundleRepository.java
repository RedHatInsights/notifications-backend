package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Bundle;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

import java.util.Optional;

@ApplicationScoped
public class BundleRepository {

    @Inject
    EntityManager entityManager;

    public Optional<Bundle> getBundle(String bundleName) {
        String query = "FROM Bundle WHERE name = :bundleName";
        try {
            return Optional.of(entityManager.createQuery(query, Bundle.class)
                .setParameter("bundleName", bundleName)
                .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
}
