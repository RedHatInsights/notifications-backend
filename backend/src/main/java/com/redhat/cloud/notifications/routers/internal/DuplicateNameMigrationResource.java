package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.kessel.KesselAuthorization;
import com.redhat.cloud.notifications.auth.kessel.ResourceType;
import com.redhat.cloud.notifications.auth.kessel.WorkspacePermission;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.routers.internal.models.DuplicateNameMigrationReport;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.auth.kessel.Constants.WORKSPACE_ID_PLACEHOLDER;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(API_INTERNAL + "/duplicate-name-migration")
public class DuplicateNameMigrationResource {
    @Inject
    BackendConfig backendConfig;

    @Inject
    KesselAuthorization kesselAuthorization;

    final String ack = "i-am-sure-i-want-to-run-the-migration";

    @Inject
    EntityManager entityManager;

    @PostConstruct
    public void initialize() {
        Log.infof("Duplicate name migration resource ack with value [%s]", ack);
    }

    @GET
    @Produces(APPLICATION_JSON)
    public DuplicateNameMigrationReport migrateDuplicateNames(@Context final SecurityContext securityContext, @QueryParam("ack") String ack) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalMigrateDuplicateNames(ack);
        } else {
            return this.legacyRBACMigrateDuplicateNames(ack);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public DuplicateNameMigrationReport legacyRBACMigrateDuplicateNames(final String ack) {
        return this.internalMigrateDuplicateNames(ack);
    }

    public DuplicateNameMigrationReport internalMigrateDuplicateNames(final String ack) {
        if (!Objects.equals(this.ack, ack)) {
            throw new BadRequestException(String.format("Invalid ack provided - should be [%s]", ack));
        }

        DuplicateNameMigrationReport migrationReport = new DuplicateNameMigrationReport();

        Log.infof("Starting migration for duplicated names on behavior groups and integrations.");

        while (true) {
            DuplicateNameMigrationReport localReport = runMigrationStep();
            migrationReport.updatedIntegrations += localReport.updatedIntegrations;
            migrationReport.updatedBehaviorGroups += localReport.updatedBehaviorGroups;

            if (localReport.updatedBehaviorGroups == 0 && localReport.updatedIntegrations == 0) {
                break;
            }
        }

        Log.infof("Finished updating duplicates names for behavior groups");

        return migrationReport;
    }

    DuplicateNameMigrationReport runMigrationStep() {
        DuplicateNameMigrationReport migrationReport = new DuplicateNameMigrationReport();

        Log.infof("Running a migration step");

        List<Object[]> repeatedIntegrationValues = entityManager.createNativeQuery(
                        // string_agg concatenates all the grouped values with the delimiter (, in this case)
                        "SELECT string_agg(CAST(id as character varying), ','), name FROM endpoints WHERE endpoint_type_v2 != :endpoint_type_email GROUP BY name, org_id HAVING count(*) > 1"
                )
                .setParameter("endpoint_type_email", EndpointType.EMAIL_SUBSCRIPTION.name())
                .getResultList();
        Log.infof(
                "Found %d different integration names across the  organizations that needs updating - each name could be used by multiple integrations",
                repeatedIntegrationValues.size()
        );

        for (Object[] repeatedValue : repeatedIntegrationValues) {
            migrationReport.updatedIntegrations += updateEndpoints(repeatedValue);
        }

        Log.infof("Finished step updating duplicates names for integrations");

        List<Object[]> repeatedBehaviorGroupValues = entityManager.createNativeQuery(
                // string_agg concatenates all the grouped values with the delimiter (, in this case)
                "SELECT string_agg(CAST(id as character varying), ',') as ids, display_name FROM behavior_group GROUP BY display_name, org_id, bundle_id HAVING count(*) > 1"
        ).getResultList();
        Log.infof(
                "Found %d different behavior group names across the orgIds/bundles that needs updating - each group could have multiple behavior groups",
                repeatedBehaviorGroupValues.size()
        );

        for (Object[] repeatedValue: repeatedBehaviorGroupValues) {
            migrationReport.updatedBehaviorGroups += updateBehaviorGroups(repeatedValue);
        }

        Log.infof("Finished step updating duplicates names for behavior groups");

        return migrationReport;
    }

    @Transactional
    int updateEndpoints(Object[] repeatedValue) {
        List<UUID> ids = Arrays.stream(repeatedValue[0].toString().split(","))
                .map(UUID::fromString)
                .collect(Collectors.toList());

        // order by creation date
        ids = entityManager.createQuery("SELECT id FROM Endpoint WHERE id IN (:ids) ORDER BY created ASC", UUID.class)
                .setParameter("ids", ids)
                .getResultList()
                .stream()
                // Dropping the first element - we are not going to update it's name.
                .skip(1)
                .collect(Collectors.toList());

        String baseName = repeatedValue[1].toString();

        for (int i = 0; i < ids.size(); ++i) {
            UUID id = ids.get(i);
            entityManager.createQuery("UPDATE Endpoint SET name = :name WHERE id = :id")
                    .setParameter("name", String.format("%s %d", baseName, i + 2))
                    .setParameter("id", id)
                    .executeUpdate();
        }

        return ids.size();
    }

    @Transactional
    int updateBehaviorGroups(Object[] repeatedValue) {
        List<UUID> ids = Arrays.stream(repeatedValue[0].toString().split(","))
                .map(UUID::fromString)
                .collect(Collectors.toList());
        String baseName = repeatedValue[1].toString();

        // order by creation date
        ids = entityManager.createQuery("SELECT id FROM BehaviorGroup WHERE id IN (:ids) ORDER BY created ASC", UUID.class)
                .setParameter("ids", ids)
                .getResultList()
                .stream()
                // Dropping the first element - we are not going to update it's name.
                .skip(1)
                .collect(Collectors.toList());

        for (int i = 0; i < ids.size(); ++i) {
            UUID id = ids.get(i);
            entityManager.createQuery("UPDATE BehaviorGroup SET displayName = :displayName WHERE id = :id")
                    .setParameter("displayName", String.format("%s %d", baseName, i + 2))
                    .setParameter("id", id)
                    .executeUpdate();
        }

        return ids.size();
    }
}
