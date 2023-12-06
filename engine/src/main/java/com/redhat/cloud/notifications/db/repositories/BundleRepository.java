package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Bundle;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.UUID;

@ApplicationScoped
public class BundleRepository {

    @Inject
    EntityManager entityManager;

    @CacheResult(cacheName = "bundle-by-id")
    public Bundle getBundle(UUID id) {
        return entityManager.find(Bundle.class, id);
    }
}
