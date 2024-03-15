package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.FULL_ACCESS;
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

    @ParameterizedTest
    @ValueSource(strings = {Constants.API_INTEGRATIONS_V_1_0, Constants.API_INTEGRATIONS_V_2_0})
    void testCreateUnsupportedEndpointType(String apiPath) {
        when(backendConfig.isEmailsOnlyModeEnabled()).thenReturn(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("account-id", "org-id", "username");
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
                .statusCode(400)
                .extract().asString();
        assertEquals(UNSUPPORTED_ENDPOINT_TYPE, responseBody);
    }

    @ParameterizedTest
    @ValueSource(strings = {Constants.API_INTEGRATIONS_V_1_0, Constants.API_INTEGRATIONS_V_2_0})
    void testUpdateUnsupportedEndpointType(String apiPath) {
        when(backendConfig.isEmailsOnlyModeEnabled()).thenReturn(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("account-id", "org-id", "username");
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        Endpoint endpoint = new Endpoint();
        endpoint.setType(WEBHOOK);
        endpoint.setName("name");
        endpoint.setDescription("description");

        String responseBody = given()
                .basePath(apiPath)
                .header(identityHeader)
                .pathParam("id", UUID.randomUUID().toString())
                .when()
                .contentType(JSON)
                .body(Json.encode(endpoint))
                .put("/endpoints/{id}")
                .then()
                .statusCode(400)
                .extract().asString();
        assertEquals(UNSUPPORTED_ENDPOINT_TYPE, responseBody);
    }

    @ParameterizedTest
    @ValueSource(strings = {Constants.API_INTEGRATIONS_V_1_0, Constants.API_INTEGRATIONS_V_2_0})
    void testDeleteUnsupportedEndpointType(String apiPath) {
        when(backendConfig.isEmailsOnlyModeEnabled()).thenReturn(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("account-id", "org-id", "username");
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        when(endpointRepository.getEndpointTypeById(anyString(), any(UUID.class))).thenReturn(CAMEL);

        String responseBody = given()
                .basePath(apiPath)
                .header(identityHeader)
                .pathParam("id", UUID.randomUUID().toString())
                .when()
                .delete("/endpoints/{id}")
                .then()
                .statusCode(400)
                .extract().asString();
        assertEquals(UNSUPPORTED_ENDPOINT_TYPE, responseBody);
    }

    @ParameterizedTest
    @ValueSource(strings = {Constants.API_INTEGRATIONS_V_1_0, Constants.API_INTEGRATIONS_V_2_0})
    void testEnableUnsupportedEndpointType(String apiPath) {
        when(backendConfig.isEmailsOnlyModeEnabled()).thenReturn(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("account-id", "org-id", "username");
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        when(endpointRepository.getEndpointTypeById(anyString(), any(UUID.class))).thenReturn(CAMEL);

        String responseBody = given()
                .basePath(apiPath)
                .header(identityHeader)
                .pathParam("id", UUID.randomUUID().toString())
                .when()
                .put("/endpoints/{id}/enable")
                .then()
                .statusCode(400)
                .extract().asString();
        assertEquals(UNSUPPORTED_ENDPOINT_TYPE, responseBody);
    }

    @ParameterizedTest
    @ValueSource(strings = {Constants.API_INTEGRATIONS_V_1_0, Constants.API_INTEGRATIONS_V_2_0})
    void testDisableUnsupportedEndpointType(String apiPath) {
        when(backendConfig.isEmailsOnlyModeEnabled()).thenReturn(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("account-id", "org-id", "username");
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        when(endpointRepository.getEndpointTypeById(anyString(), any(UUID.class))).thenReturn(CAMEL);

        String responseBody = given()
                .basePath(apiPath)
                .header(identityHeader)
                .pathParam("id", UUID.randomUUID().toString())
                .when()
                .delete("/endpoints/{id}/enable")
                .then()
                .statusCode(400)
                .extract().asString();
        assertEquals(UNSUPPORTED_ENDPOINT_TYPE, responseBody);
    }
}
