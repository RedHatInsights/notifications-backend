package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Application;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
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
