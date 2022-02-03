package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.InternalRoleAccess;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class InternalRoleAccessResources {

    @Inject
    EntityManager entityManager;

    public List<InternalRoleAccess> getByApplication(UUID applicationId) {
        final String query = "FROM InternalRoleAccess WHERE application_id = :applicationId";
        return entityManager.createQuery(query, InternalRoleAccess.class)
                .setParameter("applicationId", applicationId)
                .getResultList();
    }

    public List<InternalRoleAccess> getAll() {
        final String query = "FROM InternalRoleAccess";
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

    public List<InternalRoleAccess> getByRole(Collection<String> role) {
        final String query = "FROM InternalRoleAccess WHERE role IN (:role)";
        return entityManager.createQuery(query, InternalRoleAccess.class)
                .setParameter("role", role)
                .getResultList();
    }
}
