package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Application;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import java.util.Optional;

@ApplicationScoped
public class ApplicationRepository {

    @Inject
    EntityManager entityManager;

    public Optional<Application> getApplication(String bundleName, String applicationName) {
        String query = "FROM Application WHERE bundle.name = :bundleName AND name = :applicationName";
        try {
            return Optional.of(entityManager.createQuery(query, Application.class)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
}
