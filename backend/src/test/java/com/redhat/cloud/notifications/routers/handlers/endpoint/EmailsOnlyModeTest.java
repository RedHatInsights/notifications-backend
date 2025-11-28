package com.redhat.cloud.notifications.routers.handlers.endpoint;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.auth.kessel.KesselCheckClient;
import com.redhat.cloud.notifications.auth.kessel.KesselTestHelper;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import jakarta.inject.Inject;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.project_kessel.api.inventory.v1beta2.Allowed;

import java.util.UUID;
import java.util.stream.Stream;

import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.FULL_ACCESS;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.INTEGRATIONS_CREATE;
import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static com.redhat.cloud.notifications.routers.handlers.endpoint.EndpointResource.UNSUPPORTED_ENDPOINT_TYPE;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.project_kessel.api.inventory.v1beta2.Allowed.ALLOWED_TRUE;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EmailsOnlyModeTest extends DbIsolatedTest {

    @InjectMock
    BackendConfig backendConfig;

    @InjectMock
    EndpointRepository endpointRepository;

    @InjectMock
    KesselCheckClient kesselCheckClient;

    @InjectMock
    WorkspaceUtils workspaceUtils;

    @Inject
    KesselTestHelper kesselTestHelper;

    @BeforeEach
    void beforeEach() {
        when(workspaceUtils.getDefaultWorkspaceId(DEFAULT_ORG_ID)).thenReturn(KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID);
    }

    /**
     * Provides the required arguments for the tests.
     * @return a stream which contains argument tuples. The first element is
     * the integration API's different versioned paths and the second argument
     * states whether Kessel is enabled or not.
     */
    private static Stream<Arguments> argumentsProvider() {
        return Stream.of(
            Arguments.of(Constants.API_INTEGRATIONS_V_1_0, false),
            Arguments.of(Constants.API_INTEGRATIONS_V_1_0, true),
            Arguments.of(Constants.API_INTEGRATIONS_V_2_0, false),
            Arguments.of(Constants.API_INTEGRATIONS_V_2_0, true)
        );
    }

    @MethodSource("argumentsProvider")
    @ParameterizedTest
    void testCreateUnsupportedEndpointType(String apiPath, boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        when(backendConfig.isEmailsOnlyModeEnabled()).thenReturn(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        Endpoint endpoint = new Endpoint();
        endpoint.setType(WEBHOOK);
        endpoint.setName("name");
        endpoint.setDescription("description");

        String responseBody = given()
                .basePath(apiPath)
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(endpoint))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .extract().asString();
        assertEquals(UNSUPPORTED_ENDPOINT_TYPE, responseBody);
    }

    @MethodSource("argumentsProvider")
    @ParameterizedTest
    void testUpdateUnsupportedEndpointType(String apiPath, boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        when(backendConfig.isEmailsOnlyModeEnabled()).thenReturn(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        Endpoint endpoint = new Endpoint();
        endpoint.setType(WEBHOOK);
        endpoint.setName("name");
        endpoint.setDescription("description");

        final UUID endpointId = UUID.randomUUID();
        Mockito.when(this.endpointRepository.existsByUuidAndOrgId(endpointId, DEFAULT_ORG_ID)).thenReturn(true);

        String responseBody = given()
                .basePath(apiPath)
                .header(identityHeader)
                .pathParam("id", endpointId)
                .when()
                .contentType(JSON)
                .body(Json.encode(endpoint))
                .put("/endpoints/{id}")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .extract().asString();
        assertEquals(UNSUPPORTED_ENDPOINT_TYPE, responseBody);
    }

    @MethodSource("argumentsProvider")
    @ParameterizedTest
    void testDeleteUnsupportedEndpointType(String apiPath, boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        when(backendConfig.isEmailsOnlyModeEnabled()).thenReturn(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        when(endpointRepository.getEndpointTypeById(anyString(), any(UUID.class))).thenReturn(CAMEL);

        final UUID endpointId = UUID.randomUUID();
        Mockito.when(this.endpointRepository.existsByUuidAndOrgId(endpointId, DEFAULT_ORG_ID)).thenReturn(true);

        String responseBody = given()
                .basePath(apiPath)
                .header(identityHeader)
                .pathParam("id", endpointId)
                .when()
                .delete("/endpoints/{id}")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .extract().asString();
        assertEquals(UNSUPPORTED_ENDPOINT_TYPE, responseBody);
    }

    @MethodSource("argumentsProvider")
    @ParameterizedTest
    void testEnableUnsupportedEndpointType(String apiPath, boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        when(backendConfig.isEmailsOnlyModeEnabled()).thenReturn(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        when(endpointRepository.getEndpointTypeById(anyString(), any(UUID.class))).thenReturn(CAMEL);

        final UUID endpointId = UUID.randomUUID();
        Mockito.when(this.endpointRepository.existsByUuidAndOrgId(endpointId, DEFAULT_ORG_ID)).thenReturn(true);

        String responseBody = given()
                .basePath(apiPath)
                .header(identityHeader)
                .pathParam("id", endpointId)
                .when()
                .put("/endpoints/{id}/enable")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .extract().asString();
        assertEquals(UNSUPPORTED_ENDPOINT_TYPE, responseBody);
    }

    @MethodSource("argumentsProvider")
    @ParameterizedTest
    void testDisableUnsupportedEndpointType(String apiPath, boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        when(backendConfig.isEmailsOnlyModeEnabled()).thenReturn(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        when(endpointRepository.getEndpointTypeById(anyString(), any(UUID.class))).thenReturn(CAMEL);

        final UUID endpointId = UUID.randomUUID();
        Mockito.when(this.endpointRepository.existsByUuidAndOrgId(endpointId, DEFAULT_ORG_ID)).thenReturn(true);

        String responseBody = given()
                .basePath(apiPath)
                .header(identityHeader)
                .pathParam("id", endpointId)
                .when()
                .delete("/endpoints/{id}/enable")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .extract().asString();
        assertEquals(UNSUPPORTED_ENDPOINT_TYPE, responseBody);
    }

    private void mockDefaultKesselUpdatePermission(WorkspacePermission permission, Allowed allowed) {
        when(kesselCheckClient
            .checkForUpdate(kesselTestHelper.buildCheckForUpdateRequest(DEFAULT_USER, permission)))
            .thenReturn(kesselTestHelper.buildCheckForUpdateResponse(allowed));
    }
}
