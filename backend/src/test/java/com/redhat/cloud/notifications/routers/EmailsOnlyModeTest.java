package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.auth.kessel.KesselTestHelper;
import com.redhat.cloud.notifications.auth.kessel.ResourceType;
import com.redhat.cloud.notifications.auth.kessel.permission.IntegrationPermission;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import jakarta.inject.Inject;
import org.apache.http.HttpStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.project_kessel.relations.client.CheckClient;
import org.project_kessel.relations.client.LookupClient;

import java.util.UUID;
import java.util.stream.Stream;

import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.FULL_ACCESS;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static com.redhat.cloud.notifications.auth.kessel.Constants.WORKSPACE_ID_PLACEHOLDER;
import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static com.redhat.cloud.notifications.routers.EndpointResource.UNSUPPORTED_ENDPOINT_TYPE;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EmailsOnlyModeTest extends DbIsolatedTest {

    @InjectMock
    BackendConfig backendConfig;

    @InjectMock
    EndpointRepository endpointRepository;

    @InjectMock
    CheckClient checkClient;

    @InjectMock
    LookupClient lookupClient;

    @Inject
    KesselTestHelper kesselTestHelper;

    @Inject
    ResourceHelpers resourceHelpers;

    /**
     * Provides the required arguments for the tests.
     * @return a stream which contains argument tuples. The first element is
     * the integration API's different versioned paths and the second argument
     * states whether Kessel is enabled or not.
     */
    private static Stream<Arguments> argumentsProvider() {
        return Stream.of(
            Arguments.of(Constants.API_INTEGRATIONS_V_1_0, false),
            Arguments.of(Constants.API_INTEGRATIONS_V_2_0, true)
        );
    }

    @MethodSource("argumentsProvider")
    @ParameterizedTest
    void testCreateUnsupportedEndpointType(final String apiPath, final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        when(backendConfig.isEmailsOnlyModeEnabled()).thenReturn(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        Endpoint endpoint = new Endpoint();
        endpoint.setType(WEBHOOK);
        endpoint.setName("name");
        endpoint.setDescription("description");

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);
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
    void testUpdateUnsupportedEndpointType(final String apiPath, final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

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
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.EDIT, ResourceType.INTEGRATION, endpointId.toString());

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
    void testDeleteUnsupportedEndpointType(final String apiPath, final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        when(backendConfig.isEmailsOnlyModeEnabled()).thenReturn(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        when(endpointRepository.getEndpointTypeById(anyString(), any(UUID.class))).thenReturn(CAMEL);

        final UUID endpointId = UUID.randomUUID();
        Mockito.when(this.endpointRepository.existsByUuidAndOrgId(endpointId, DEFAULT_ORG_ID)).thenReturn(true);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.DELETE, ResourceType.INTEGRATION, endpointId.toString());

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
    void testEnableUnsupportedEndpointType(final String apiPath, final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        when(backendConfig.isEmailsOnlyModeEnabled()).thenReturn(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        when(endpointRepository.getEndpointTypeById(anyString(), any(UUID.class))).thenReturn(CAMEL);

        final UUID endpointId = UUID.randomUUID();
        Mockito.when(this.endpointRepository.existsByUuidAndOrgId(endpointId, DEFAULT_ORG_ID)).thenReturn(true);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.ENABLE, ResourceType.INTEGRATION, endpointId.toString());

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
    void testDisableUnsupportedEndpointType(final String apiPath, final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        when(backendConfig.isEmailsOnlyModeEnabled()).thenReturn(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        when(endpointRepository.getEndpointTypeById(anyString(), any(UUID.class))).thenReturn(CAMEL);

        final UUID endpointId = UUID.randomUUID();
        Mockito.when(this.endpointRepository.existsByUuidAndOrgId(endpointId, DEFAULT_ORG_ID)).thenReturn(true);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.DISABLE, ResourceType.INTEGRATION, endpointId.toString());

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
}
