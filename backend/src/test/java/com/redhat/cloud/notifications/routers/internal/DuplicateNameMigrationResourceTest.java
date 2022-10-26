package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.routers.internal.models.DuplicateNameMigrationReport;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class DuplicateNameMigrationResourceTest extends DbIsolatedTest {

    private final String UNUSED = "UNUSED";

    final String orgId1 = "duplicateOrgId1";
    final String orgId2 = "duplicateOrgId2";
    final String orgId3 = "duplicateOrgId3";
    final String orgId4 = "duplicateOrgId4";

    final String nameA = "name-a";
    final String nameB = "name-b";
    final String nameC = "name-c";
    final String nameD = "name-d";

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    DuplicateNameMigrationResource duplicateNameMigrationResource;

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Test
    public void shouldWorkCorrectlyIfNoDuplicates() {
        UUID bundle1 = resourceHelpers.createBundle().getId();

        createEndpoint(orgId1, EndpointType.WEBHOOK, nameA);
        createEndpoint(orgId2, EndpointType.WEBHOOK, nameB);
        createEndpoint(orgId3, EndpointType.WEBHOOK, nameC);
        createEndpoint(orgId4, EndpointType.WEBHOOK, nameD);

        createBehaviorGroup(orgId1, bundle1, nameA);
        createBehaviorGroup(orgId2, bundle1, nameB);
        createBehaviorGroup(orgId3, bundle1, nameC);
        createBehaviorGroup(orgId4, bundle1, nameD);

        DuplicateNameMigrationReport report = runMigration();
        assertEquals(0, report.updatedBehaviorGroups);
        assertEquals(0, report.updatedIntegrations);
    }

    @Test
    public void shouldAllowSameNameAcrossBundles() {
        UUID bundle1 = resourceHelpers.createBundle().getId();

        createEndpoint(orgId1, EndpointType.WEBHOOK, nameA);
        createEndpoint(orgId2, EndpointType.WEBHOOK, nameA);
        createEndpoint(orgId3, EndpointType.WEBHOOK, nameA);
        createEndpoint(orgId4, EndpointType.WEBHOOK, nameA);

        createBehaviorGroup(orgId1, bundle1, nameA);
        createBehaviorGroup(orgId2, bundle1, nameA);
        createBehaviorGroup(orgId3, bundle1, nameA);
        createBehaviorGroup(orgId4, bundle1, nameA);

        DuplicateNameMigrationReport report = runMigration();
        assertEquals(0, report.updatedBehaviorGroups);
        assertEquals(0, report.updatedIntegrations);
    }

    @Test
    public void duplicateEndpointsAreNamedIncrementallyOnCreationDate() {
        List<Endpoint> duplicatedEndpoints1 = new ArrayList<>();
        createEndpoint(orgId1, EndpointType.WEBHOOK, nameA, LocalDateTime.now().plusDays(2), duplicatedEndpoints1); // 2
        createEndpoint(orgId1, EndpointType.WEBHOOK, nameA, LocalDateTime.now().plusDays(3), duplicatedEndpoints1); // 3
        createEndpoint(orgId1, EndpointType.WEBHOOK, nameA, LocalDateTime.now().plusDays(1), duplicatedEndpoints1); // 1
        createEndpoint(orgId1, EndpointType.WEBHOOK, nameA, LocalDateTime.now().plusDays(4), duplicatedEndpoints1); // 4

        DuplicateNameMigrationReport report = runMigration();
        assertEquals(0, report.updatedBehaviorGroups);
        assertEquals(3, report.updatedIntegrations); // 1 of each group of duplicates is not updated

        assertEquals("name-a 2", getUpdatedEndpointName(duplicatedEndpoints1.get(0)));
        assertEquals("name-a 3", getUpdatedEndpointName(duplicatedEndpoints1.get(1)));
        assertEquals("name-a", getUpdatedEndpointName(duplicatedEndpoints1.get(2)));
        assertEquals("name-a 4", getUpdatedEndpointName(duplicatedEndpoints1.get(3)));
    }

    @Test
    public void duplicateBehaviorGroupsAreNamedIncrementallyOnCreationDate() {
        UUID bundle1 = resourceHelpers.createBundle().getId();

        List<BehaviorGroup> duplicatedBehaviorGroups1 = new ArrayList<>();
        createBehaviorGroup(orgId1, bundle1, nameA, LocalDateTime.now().plusDays(3), duplicatedBehaviorGroups1); // 3
        createBehaviorGroup(orgId1, bundle1, nameA, LocalDateTime.now().plusDays(2), duplicatedBehaviorGroups1); // 2
        createBehaviorGroup(orgId1, bundle1, nameA, LocalDateTime.now().plusDays(4), duplicatedBehaviorGroups1); // 4
        createBehaviorGroup(orgId1, bundle1, nameA, LocalDateTime.now().plusDays(1), duplicatedBehaviorGroups1); // 1

        DuplicateNameMigrationReport report = runMigration();
        assertEquals(3, report.updatedBehaviorGroups);
        assertEquals(0, report.updatedIntegrations); // 1 of each group of duplicates is not updated

        assertEquals("name-a 3", getUpdatedBehaviorGroupName(duplicatedBehaviorGroups1.get(0)));
        assertEquals("name-a 2", getUpdatedBehaviorGroupName(duplicatedBehaviorGroups1.get(1)));
        assertEquals("name-a 4", getUpdatedBehaviorGroupName(duplicatedBehaviorGroups1.get(2)));
        assertEquals("name-a", getUpdatedBehaviorGroupName(duplicatedBehaviorGroups1.get(3)));
    }

    @Test
    public void shouldMigrateDuplicateEndpoints() {
        List<Endpoint> duplicatedEndpoints1 = new ArrayList<>();
        List<Endpoint> duplicatedEndpoints2 = new ArrayList<>();
        List<Endpoint> duplicatedEndpoints3 = new ArrayList<>();
        List<Endpoint> duplicatedEndpoints4 = new ArrayList<>();

        List<Endpoint> notDuplicated = new ArrayList<>();

        // Org 1: nameA duplicated 3 times
        createEndpoint(orgId1, EndpointType.WEBHOOK, nameA, duplicatedEndpoints1);
        createEndpoint(orgId1, EndpointType.WEBHOOK, nameA, duplicatedEndpoints1);
        createEndpoint(orgId1, EndpointType.WEBHOOK, nameA, duplicatedEndpoints1);
        createEndpoint(orgId1, EndpointType.WEBHOOK, nameB, notDuplicated);

        // Org 2: nameA duplicated 2 times
        createEndpoint(orgId2, EndpointType.WEBHOOK, nameA, duplicatedEndpoints2);
        createEndpoint(orgId2, EndpointType.WEBHOOK, nameA, duplicatedEndpoints2);
        createEndpoint(orgId2, EndpointType.WEBHOOK, nameB, notDuplicated);
        createEndpoint(orgId2, EndpointType.WEBHOOK, nameC, notDuplicated);

        // Org 3: nameB duplicated 4 times and nameC 3 times
        createEndpoint(orgId3, EndpointType.WEBHOOK, nameB, duplicatedEndpoints3);
        createEndpoint(orgId3, EndpointType.WEBHOOK, nameB, duplicatedEndpoints3);
        createEndpoint(orgId3, EndpointType.WEBHOOK, nameB, duplicatedEndpoints3);
        createEndpoint(orgId3, EndpointType.WEBHOOK, nameB, duplicatedEndpoints3);

        createEndpoint(orgId3, EndpointType.WEBHOOK, nameC, duplicatedEndpoints4);
        createEndpoint(orgId3, EndpointType.WEBHOOK, nameC, duplicatedEndpoints4);
        createEndpoint(orgId3, EndpointType.WEBHOOK, nameC, duplicatedEndpoints4);

        DuplicateNameMigrationReport report = runMigration();
        assertEquals(8, report.updatedIntegrations); // 1 of each group of duplicates is not updated
        assertEquals(0, report.updatedBehaviorGroups);

        assertEndpointList(duplicatedEndpoints1, true);
        assertEndpointList(duplicatedEndpoints2, true);
        assertEndpointList(duplicatedEndpoints3, true);
        assertEndpointList(duplicatedEndpoints4, true);
        assertEndpointList(notDuplicated, false);
    }

    @Test
    public void shouldMigrateDuplicateBehaviorGroups() {
        UUID bundle1 = resourceHelpers.createBundle("name1", "displayName1").getId();
        UUID bundle2 = resourceHelpers.createBundle("name2", "displayName2").getId();

        List<BehaviorGroup> duplicated1 = new ArrayList<>();
        List<BehaviorGroup> duplicated2 = new ArrayList<>();
        List<BehaviorGroup> duplicated3 = new ArrayList<>();
        List<BehaviorGroup> duplicated4 = new ArrayList<>();

        List<BehaviorGroup> notDuplicated = new ArrayList<>();

        // Behavior groups names repeated are per (orgId, bundle)
        // 3 orgId1, bundle1
        createBehaviorGroup(orgId1, bundle1, nameA, duplicated1);
        createBehaviorGroup(orgId1, bundle1, nameA, duplicated1);
        createBehaviorGroup(orgId1, bundle1, nameA, duplicated1);

        createBehaviorGroup(orgId1, bundle1, nameB, notDuplicated);
        createBehaviorGroup(orgId1, bundle1, nameC, notDuplicated);

        // 5 orgId1, bundle2
        createBehaviorGroup(orgId1, bundle2, nameA, duplicated2);
        createBehaviorGroup(orgId1, bundle2, nameA, duplicated2);
        createBehaviorGroup(orgId1, bundle2, nameA, duplicated2);
        createBehaviorGroup(orgId1, bundle2, nameA, duplicated2);
        createBehaviorGroup(orgId1, bundle2, nameA, duplicated2);

        createBehaviorGroup(orgId1, bundle2, nameB, notDuplicated);
        createBehaviorGroup(orgId1, bundle2, nameC, notDuplicated);

        // 2 orgId2, bundle1
        createBehaviorGroup(orgId2, bundle1, nameA, duplicated3);
        createBehaviorGroup(orgId2, bundle1, nameA, duplicated3);

        createBehaviorGroup(orgId2, bundle1, nameB, notDuplicated);
        createBehaviorGroup(orgId2, bundle1, nameC, notDuplicated);

        // 2 orgId3, bundle2
        createBehaviorGroup(orgId3, bundle2, nameA, duplicated4);
        createBehaviorGroup(orgId3, bundle2, nameA, duplicated4);

        createBehaviorGroup(orgId3, bundle2, nameB, notDuplicated);
        createBehaviorGroup(orgId3, bundle2, nameC, notDuplicated);

        DuplicateNameMigrationReport report = runMigration();
        assertEquals(0, report.updatedIntegrations);
        assertEquals(8, report.updatedBehaviorGroups);  // 1 of each group of duplicates is not updated

        assertBehaviorGroupList(duplicated1, true);
        assertBehaviorGroupList(duplicated2, true);
        assertBehaviorGroupList(duplicated3, true);
        assertBehaviorGroupList(duplicated4, true);
        assertBehaviorGroupList(notDuplicated, false);
    }

    @Test
    public void mightRequireMultipleSteps() {
        UUID bundle1 = resourceHelpers.createBundle().getId();
        List<Endpoint> duplicatedEndpoints = new ArrayList<>();
        List<BehaviorGroup> duplicatedBehaviorGroups = new ArrayList<>();

        createEndpoint(orgId1, EndpointType.WEBHOOK, nameA, duplicatedEndpoints);
        createEndpoint(orgId1, EndpointType.WEBHOOK, nameA, duplicatedEndpoints);
        createEndpoint(orgId1, EndpointType.WEBHOOK, nameA, duplicatedEndpoints);

        // This will be a duplicate on the first run
        createEndpoint(orgId1, EndpointType.WEBHOOK, "name-a 2", duplicatedEndpoints);
        createEndpoint(orgId1, EndpointType.WEBHOOK, "name-a 3", duplicatedEndpoints);

        // This will be a duplicate on the second run
        createEndpoint(orgId1, EndpointType.WEBHOOK, "name-a 2 2", duplicatedEndpoints);

        createBehaviorGroup(orgId1, bundle1, nameA, duplicatedBehaviorGroups);
        createBehaviorGroup(orgId1, bundle1, nameA, duplicatedBehaviorGroups);
        createBehaviorGroup(orgId1, bundle1, nameA, duplicatedBehaviorGroups);
        createBehaviorGroup(orgId1, bundle1, nameA, duplicatedBehaviorGroups);

        // This will be a duplicate on the first run
        createBehaviorGroup(orgId1, bundle1, "name-a 2", duplicatedBehaviorGroups);
        createBehaviorGroup(orgId1, bundle1, "name-a 4", duplicatedBehaviorGroups);

        // This will be a duplicate on the second run
        createBehaviorGroup(orgId1, bundle1, "name-a 2 2", duplicatedBehaviorGroups);

        DuplicateNameMigrationReport report = runMigration();

        assertEquals(6, report.updatedBehaviorGroups); // 3 first step, 2 second step, 1 third step
        assertEquals(5, report.updatedIntegrations); // 2 first step, 2 second step and 1 in third step
    }

    private void createBehaviorGroup(String orgId, UUID bundleId, String name, List<BehaviorGroup> behaviorGroupList) {
        createBehaviorGroup(orgId, bundleId, name, null, behaviorGroupList);
    }

    private void createBehaviorGroup(String orgId, UUID bundleId, String name, LocalDateTime created, List<BehaviorGroup> behaviorGroupList) {
        BehaviorGroup behaviorGroup = resourceHelpers.createBehaviorGroup(UNUSED, orgId, name, bundleId, created);
        if (behaviorGroupList != null) {
            behaviorGroupList.add(behaviorGroup);
        }
    }

    private void createBehaviorGroup(String orgId, UUID bundleId, String name) {
        createBehaviorGroup(orgId, bundleId, name, null);
    }

    private String getUpdatedBehaviorGroupName(BehaviorGroup behaviorGroup) {
        return resourceHelpers.getBehaviorGroup(behaviorGroup.getId()).getDisplayName();
    }

    private void assertBehaviorGroupList(List<BehaviorGroup> behaviorGroupList, boolean areDuplicates) {
        if (areDuplicates) {
            for (int i = 0; i < behaviorGroupList.size(); i++) {
                BehaviorGroup behaviorGroup = behaviorGroupList.get(i);
                BehaviorGroup other = resourceHelpers.getBehaviorGroup(behaviorGroup.getId());

                if (i == 0) {
                    assertEquals(behaviorGroup.getDisplayName(), other.getDisplayName());
                } else {
                    assertEquals(String.format("%s %d", behaviorGroup.getDisplayName(), i + 1), other.getDisplayName());
                }
            }
        } else {
            for (BehaviorGroup behaviorGroup : behaviorGroupList) {
                BehaviorGroup other = resourceHelpers.getBehaviorGroup(behaviorGroup.getId());
                assertEquals(other.getDisplayName(), other.getDisplayName());
            }
        }
    }

    private void createEndpoint(String orgId, EndpointType endpointType, String name, List<Endpoint> endpointList) {
        createEndpoint(orgId, endpointType, name, null, endpointList);
    }

    private void createEndpoint(String orgId, EndpointType endpointType, String name, LocalDateTime created, List<Endpoint> endpointList) {
        Endpoint endpoint = resourceHelpers.createEndpoint(UNUSED, orgId, endpointType, null, name, UNUSED, null, true, created);
        if (endpointList != null) {
            endpointList.add(endpoint);
        }
    }

    private void createEndpoint(String orgId, EndpointType endpointType, String name) {
        createEndpoint(orgId, endpointType, name, null, null);
    }

    private String getUpdatedEndpointName(Endpoint endpoint) {
        Endpoint other = resourceHelpers.getEndpoint(endpoint.getOrgId(), endpoint.getId());
        return other.getName();
    }

    private void assertEndpointList(List<Endpoint> endpointList, boolean areDuplicates) {
        if (areDuplicates) {
            for (int i = 0; i < endpointList.size(); i++) {
                Endpoint endpoint = endpointList.get(i);

                Endpoint other = resourceHelpers.getEndpoint(endpoint.getOrgId(), endpoint.getId());

                if (i == 0) {
                    assertEquals(endpoint.getName(), other.getName());
                } else {
                    assertEquals(String.format("%s %d", endpoint.getName(), i + 1), other.getName());
                }
            }
        } else {
            for (Endpoint endpoint : endpointList) {
                Endpoint other = resourceHelpers.getEndpoint(endpoint.getOrgId(), endpoint.getId());
                assertEquals(endpoint.getName(), other.getName());
            }
        }
    }

    private DuplicateNameMigrationReport runMigration() {
        Header turnpikeAdminHeader = TestHelpers.createTurnpikeIdentityHeader("admin", adminRole);

        return given()
                .header(turnpikeAdminHeader)
                .queryParam("ack", duplicateNameMigrationResource.ack)
                .get("/internal/duplicate-name-migration")
                .then()
                .contentType(JSON)
                .statusCode(200)
                .extract().as(DuplicateNameMigrationReport.class);
    }
}
