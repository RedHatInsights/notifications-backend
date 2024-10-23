package com.redhat.cloud.notifications.routers.internal.kessel;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.auth.kessel.ResourceType;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.StatusRepository;
import com.redhat.cloud.notifications.models.CurrentStatus;
import com.redhat.cloud.notifications.models.Status;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.project_kessel.api.relations.v1beta1.CreateTuplesRequest;
import org.project_kessel.api.relations.v1beta1.ObjectReference;
import org.project_kessel.api.relations.v1beta1.Relationship;
import org.project_kessel.relations.client.RelationTuplesClient;

import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static io.restassured.RestAssured.given;

@QuarkusTest
public class KesselAssetsMigrationServiceTest extends DbIsolatedTest {
    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @InjectMock
    BackendConfig backendConfig;

    @InjectMock
    RelationTuplesClient relationTuplesClient;

    @InjectMock
    StatusRepository statusRepository;

    @InjectMock
    WorkspaceUtils workspaceUtils;

    @Inject
    ResourceHelpers resourceHelpers;

    /**
     * Tests that if the application isn't in maintenance mode the migration is
     * not run, and a "bad request" response is returned.
     */
    @Test
    void testNonMaintenanceModeBadRequest() {
        // Simulate that the application is operating normally.
        final CurrentStatus currentStatus = new CurrentStatus();
        currentStatus.setStatus(Status.UP);
        Mockito.when(this.statusRepository.getCurrentStatus()).thenReturn(currentStatus);

        given()
            .when()
            .header(TestHelpers.createTurnpikeIdentityHeader(DEFAULT_USER, adminRole))
            .post(API_INTERNAL + "/kessel/migrate-assets")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    /**
     * Tests that when the integrations to migrate are less than the batch
     * size, only one request is sent to Kessel.
     */
    @Test
    void testMigrateLessThanBatchSizeAssetsKessel() {
        // Simulate that the application is in maintenance mode.
        final CurrentStatus currentStatus = new CurrentStatus();
        currentStatus.setStatus(Status.MAINTENANCE);
        Mockito.when(this.statusRepository.getCurrentStatus()).thenReturn(currentStatus);

        // Simulate that the maximum batch size is 10 endpoints.
        Mockito.when(this.backendConfig.getKesselMigrationBatchSize()).thenReturn(10);
        final int integrationsToCreate = this.backendConfig.getKesselMigrationBatchSize() - 1;

        // Mock the response we would get from RBAC when asking for the default
        // workspace.
        final UUID workspaceId = UUID.randomUUID();
        Mockito.when(this.workspaceUtils.getDefaultWorkspaceId(DEFAULT_ORG_ID)).thenReturn(workspaceId);

        this.resourceHelpers.createTestEndpoints(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, integrationsToCreate);

        given()
            .when()
            .header(TestHelpers.createTurnpikeIdentityHeader(DEFAULT_USER, adminRole))
            .post(API_INTERNAL + "/kessel/migrate-assets")
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);

        // Assert that the correct number of requests was generated for Kessel.
        final ArgumentCaptor<CreateTuplesRequest> createTuplesRequestArgumentCaptor = ArgumentCaptor.forClass(CreateTuplesRequest.class);
        Mockito.verify(this.relationTuplesClient, Mockito.times(1)).createTuples(createTuplesRequestArgumentCaptor.capture(), Mockito.any());

        Assertions.assertEquals(
            1,
            createTuplesRequestArgumentCaptor.getAllValues().size(),
            String.format("[maximum_batch_size: %s][calls_to_kessel: %s] Only one request should have been sent to Kessel, since the ", this.backendConfig.getKesselMigrationBatchSize(), createTuplesRequestArgumentCaptor.getAllValues().size())
        );

        // Assert that the "create request" has the expected data.
        final CreateTuplesRequest createTuplesRequest = createTuplesRequestArgumentCaptor.getAllValues().getFirst();
        this.assertCreateRequestIsCorrect(integrationsToCreate, workspaceId, createTuplesRequest);
    }

    /**
     * Tests that when the integrations to migrate are more than the batch
     * size, multiple requests are sent to Kessel.
     */
    @Test
    void testMigrateMoreThanBatchSizeAssetsKessel() {
        // Simulate that the application is in maintenance mode.
        final CurrentStatus currentStatus = new CurrentStatus();
        currentStatus.setStatus(Status.MAINTENANCE);
        Mockito.when(this.statusRepository.getCurrentStatus()).thenReturn(currentStatus);

        // Simulate that the maximum batch size is 10 endpoints.
        Mockito.when(this.backendConfig.getKesselMigrationBatchSize()).thenReturn(10);
        final int integrationsToCreate = this.backendConfig.getKesselMigrationBatchSize() * 3;

        // Mock the response we would get from RBAC when asking for the default
        // workspace.
        final UUID workspaceId = UUID.randomUUID();
        Mockito.when(this.workspaceUtils.getDefaultWorkspaceId(DEFAULT_ORG_ID)).thenReturn(workspaceId);

        this.resourceHelpers.createTestEndpoints(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, integrationsToCreate);

        given()
            .when()
            .header(TestHelpers.createTurnpikeIdentityHeader(DEFAULT_USER, adminRole))
            .post(API_INTERNAL + "/kessel/migrate-assets")
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);

        // Assert that the correct number of requests was generated for Kessel.
        final ArgumentCaptor<CreateTuplesRequest> createTuplesRequestArgumentCaptor = ArgumentCaptor.forClass(CreateTuplesRequest.class);
        Mockito.verify(this.relationTuplesClient, Mockito.times(3)).createTuples(createTuplesRequestArgumentCaptor.capture(), Mockito.any());

        Assertions.assertEquals(
            3,
            createTuplesRequestArgumentCaptor.getAllValues().size(),
            String.format("[maximum_batch_size: %s][calls_to_kessel: %s] Only one request should have been sent to Kessel, since the ", this.backendConfig.getKesselMigrationBatchSize(), createTuplesRequestArgumentCaptor.getAllValues().size())
        );

        // Assert that the "create request" has the expected data.
        for (final CreateTuplesRequest createTuplesRequest : createTuplesRequestArgumentCaptor.getAllValues()) {
            this.assertCreateRequestIsCorrect(this.backendConfig.getKesselMigrationBatchSize(), workspaceId, createTuplesRequest);
        }
    }

    /**
     * Asserts that the given "create request" has the correct data.
     * @param expectedTuplesToFind expected tuples to find inside the request.
     * @param createTuplesRequest the request itself which is going to be
     *                            asserted.
     */
    private void assertCreateRequestIsCorrect(final int expectedTuplesToFind, final UUID expectedWorkspaceId, final CreateTuplesRequest createTuplesRequest) {
        final List<Relationship> relationshipTuples = createTuplesRequest.getTuplesList();

        Assertions.assertEquals(
            expectedTuplesToFind,
            relationshipTuples.size(),
            "Unexpected number of tuples created in the request"
        );

        for (final Relationship relationship : relationshipTuples) {
            final ObjectReference objectReference = relationship.getResource();
            Assertions.assertEquals(ResourceType.INTEGRATION.getKesselObjectType(), objectReference.getType(), "unexpected type set in the tuple");
            Assertions.assertNotNull(objectReference.getId(), "the ID of the object reference is null, when the endpoint's ID should have been set instead");

            Assertions.assertEquals(KesselAssetsMigrationService.RELATION, relationship.getRelation(), "unexpected relation set in the tuple");

            final ObjectReference subjectReference = relationship.getSubject().getSubject();
            Assertions.assertEquals("rbac", subjectReference.getType().getNamespace(), "incorrect namespace set for tuple");
            Assertions.assertEquals("workspace", subjectReference.getType().getName(), "incorrect name set for tuple");
            Assertions.assertEquals(expectedWorkspaceId.toString(), subjectReference.getId(), "incorrect workspace ID set in tuple");
        }
    }
}
