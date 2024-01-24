package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Bundle;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class BundleRepository {

    @Inject
    EntityManager entityManager;

    @CacheResult(cacheName = "bundle-by-id")
    public Bundle getBundle(UUID id) {
        return entityManager.find(Bundle.class, id);
    }

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
