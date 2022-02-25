package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.transaction.Transactional;
import javax.ws.rs.NotFoundException;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class BundleResources {

    @Inject
    EntityManager entityManager;

    @Transactional
    public Bundle createBundle(Bundle bundle) {
        // The returned bundle will contain an ID and a creation timestamp.
        entityManager.persist(bundle);
        return bundle;
    }

    public List<Bundle> getBundles() {
        String query = "FROM Bundle";
        return entityManager.createQuery(query, Bundle.class)
                .getResultList();
    }

    public Bundle getBundle(UUID id) {
        return entityManager.find(Bundle.class, id);
    }

    public Bundle getBundle(String name) {
        String query = "FROM Bundle WHERE name = :name";
        try {
            return entityManager.createQuery(query, Bundle.class)
                    .setParameter("name", name)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Transactional
    public int updateBundle(UUID id, Bundle bundle) {
        String bundleQuery = "UPDATE Bundle SET name = :name, displayName = :displayName WHERE id = :id";
        int rowCount = entityManager.createQuery(bundleQuery)
                .setParameter("name", bundle.getName())
                .setParameter("displayName", bundle.getDisplayName())
                .setParameter("id", id)
                .executeUpdate();
        String eventQuery = "UPDATE Event SET bundleDisplayName = :displayName WHERE bundleId = :bundleId";
        entityManager.createQuery(eventQuery)
                .setParameter("displayName", bundle.getDisplayName())
                .setParameter("bundleId", id)
                .executeUpdate();
        return rowCount;
    }

    @Transactional
    public boolean deleteBundle(UUID id) {
        String query = "DELETE FROM Bundle WHERE id = :id";
        int rowCount = entityManager.createQuery(query)
                .setParameter("id", id)
                .executeUpdate();
        return rowCount > 0;
    }

    public List<Application> getApplications(UUID id) {
        String query = "FROM Application WHERE bundle.id = :id";
        Bundle bundle = entityManager.find(Bundle.class, id);
        if (bundle == null) {
            throw new NotFoundException();
        } else {
            return entityManager.createQuery(query, Application.class)
                    .setParameter("id", id)
                    .getResultList();
        }
    }
}
