package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.InternalRoleAccess;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class InternalRoleAccessRepository {

    @Inject
    EntityManager entityManager;

    public List<InternalRoleAccess> getByApplication(UUID applicationId) {
        final String query = "FROM InternalRoleAccess WHERE application.id = :applicationId ORDER BY id DESC";
        return entityManager.createQuery(query, InternalRoleAccess.class)
                .setParameter("applicationId", applicationId)
                .getResultList();
    }

    /**
     * Get a single role for the given application.
     * @param applicationId the application to look the role for.
     * @return the associated role for the application or {code null}.
     */
    public InternalRoleAccess findOneByApplicationUUID(final UUID applicationId) {
        final String query =
            "FROM " +
                "InternalRoleAccess " +
            "WHERE " +
                "application.id = :applicationId " +
            "ORDER BY " +
                "id " +
            "DESC " +
            "LIMIT 1";

        try {
            return this.entityManager.createQuery(query, InternalRoleAccess.class)
                .setParameter("applicationId", applicationId)
                .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public List<InternalRoleAccess> getAll() {
        final String query = "FROM InternalRoleAccess ORDER BY id DESC";
        return entityManager.createQuery(query, InternalRoleAccess.class).getResultList();
    }

    @Transactional
    public InternalRoleAccess addAccess(InternalRoleAccess access) {
        entityManager.persist(access);
        return access;
    }

    @Transactional
    public void removeAccess(UUID internalRoleAccessId) {
        final String query = "DELETE FROM InternalRoleAccess WHERE id = :id";
        entityManager.createQuery(query)
                .setParameter("id", internalRoleAccessId)
                .executeUpdate();
    }

    public List<InternalRoleAccess> getByRoles(Collection<String> roles) {
        final String query = "FROM InternalRoleAccess WHERE role IN (:role) ORDER BY id DESC";
        return entityManager.createQuery(query, InternalRoleAccess.class)
                .setParameter("role", roles)
                .getResultList();
    }
}
