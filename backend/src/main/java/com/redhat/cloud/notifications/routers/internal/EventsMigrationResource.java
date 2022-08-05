package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.orgid.OrgIdTranslator;
import io.quarkus.logging.Log;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;

import java.util.List;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN;

@RolesAllowed(RBAC_INTERNAL_ADMIN)
@Path(API_INTERNAL)
public class EventsMigrationResource {

    @Inject
    EntityManager entityManager;

    @Inject
    OrgIdTranslator orgIdTranslator;

    @PUT
    @Path("/org-id/migrate")
    public void migrate() {
        Log.info("Events migration starting");
        String hql = "SELECT DISTINCT(accountId) FROM Event WHERE orgId IS NULL";
        List<String> accountIds = entityManager.createQuery(hql, String.class).getResultList();
        Log.infof("Found %d account(s) with events to migrate", accountIds.size());
        for (String accountId : accountIds) {
            migrateAccountId(accountId);
        }
        Log.info("Events migration ended");
    }

    @Transactional
    void migrateAccountId(String accountId) {
        Log.infof("Migrating events of account %s", accountId);
        String orgId = orgIdTranslator.translate(accountId);
        if (orgId == null) {
            Log.warnf("Events from account %s won't be migrated because of the unknown EAN", accountId);
        } else {
            String hql = "UPDATE Event SET orgId = :orgId WHERE accountId = :accountId AND orgId IS NULL";
            int updated = entityManager.createQuery(hql)
                    .setParameter("accountId", accountId)
                    .setParameter("orgId", orgId)
                    .executeUpdate();
            Log.infof("%d record(s) updated", updated);
        }
    }
}
