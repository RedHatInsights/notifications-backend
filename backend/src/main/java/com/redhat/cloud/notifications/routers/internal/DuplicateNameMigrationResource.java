package com.redhat.cloud.notifications.routers.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.routers.internal.models.DuplicateNameMigrationReport;
import io.quarkus.logging.Log;

import javax.annotation.PostConstruct;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;

@RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
@Path(API_INTERNAL + "/duplicate-name-migration")
public class DuplicateNameMigrationResource {

    private String key;

    @Inject
    EntityManager entityManager;

    @PostConstruct
    public void initialize() {
        key = UUID.randomUUID().toString();
        Log.infof("Duplicate name migration resource key with value [%s]", key);
    }

    @GET
    @Produces(APPLICATION_JSON)
    public DuplicateNameMigrationReport migrateDuplicateNames(@QueryParam("key") String key) {
        if (!Objects.equals(this.key, key)) {
            throw new BadRequestException("Invalid key provided");
        }

        DuplicateNameMigrationReport migrationReport = new DuplicateNameMigrationReport();

        Log.infof("Starting migration for duplicated names on behavior groups and integrations.");

        List<String[]> repeatedIntegrationValues = entityManager.createNativeQuery(
                // string_agg concatenates all the grouped values with the delimiter (, in this case)
                "SELECT string_agg(id::character varying, ','), name FROM endpoints WHERE endpoint_type != :endpoint_type_email GROUP BY name, org_id HAVING count(*) > 1"
        )
        .setParameter("endpoint_type_email", EndpointType.EMAIL_SUBSCRIPTION.ordinal())
        .getResultList();
        Log.infof("%d organizations needs to migrate their integration names", repeatedIntegrationValues.size());

        for (String[] repeatedValue : repeatedIntegrationValues) {
            List<UUID> ids = Arrays.stream(repeatedValue[0].split(","))
                    .map(UUID::fromString)
                    // Dropping the first element - we are not going to update it's name.
                    .skip(1)
                    .collect(Collectors.toList());
            String baseName = repeatedValue[1];

            for (int i = 0; i < ids.size(); ++i) {
                UUID id = ids.get(i);
                entityManager.createQuery("UPDATE Endpoint SET name = :name WHERE id = :id")
                        .setParameter("name", String.format("%s %d", baseName, i + 2))
                        .setParameter("id", id)
                        .executeUpdate();
                migrationReport.updatedIntegrations++;
            }
        }

        Log.infof("Finished updating duplicates names for integrations");

        List<String[]> repeatedBehaviorGroupValues = entityManager.createNativeQuery(
                "SELECT string_agg(id::character varying, ',') as ids, display_name FROM behavior_group GROUP BY display_name, org_id, bundle_id HAVING count(*) > 1"
        ).getResultList();
        Log.infof("%d organizations-bundles needs to migrate their behavior group display name", repeatedIntegrationValues.size());

        for (String[] repeatedValue: repeatedBehaviorGroupValues) {
            List<UUID> ids = Arrays.stream(repeatedValue[0].split(","))
                    .map(UUID::fromString)
                    // Dropping the first element - we are not going to update it's name.
                    .skip(1)
                    .collect(Collectors.toList());
            String baseName = repeatedValue[1];

            for (int i = 0; i < ids.size(); ++i) {
                UUID id = ids.get(i);
                entityManager.createQuery("UPDATE BehaviorGroup SET displayName = :displayName WHERE id = :id")
                        .setParameter("displayName", String.format("%s %d", baseName, i + 2))
                        .setParameter("id", id)
                        .executeUpdate();
                migrationReport.updatedBehaviorGroups++;
            }
        }

        Log.infof("Finished updating duplicates names for behavior groups");

        return migrationReport;
    }
}
