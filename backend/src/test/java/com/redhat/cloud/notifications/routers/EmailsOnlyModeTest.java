package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.http.Header;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_INTEGRATIONS_V_1_0;
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

    @Inject
    FeatureFlipper featureFlipper;

    @InjectMock
    EndpointRepository endpointRepository;

    @Test
    void testCreateUnsupportedEndpointType() {
        featureFlipper.setEmailsOnlyMode(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("account-id", "org-id", "username");
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        Endpoint endpoint = new Endpoint();
        endpoint.setType(WEBHOOK);
        endpoint.setName("name");
        endpoint.setDescription("description");

        String responseBody = given()
                .basePath(API_INTEGRATIONS_V_1_0)
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(endpoint))
                .post("/endpoints")
                .then()
                .statusCode(400)
                .extract().asString();
        assertEquals(UNSUPPORTED_ENDPOINT_TYPE, responseBody);

        featureFlipper.setEmailsOnlyMode(false);
    }

    @Test
    void testUpdateUnsupportedEndpointType() {
        featureFlipper.setEmailsOnlyMode(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("account-id", "org-id", "username");
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        Endpoint endpoint = new Endpoint();
        endpoint.setType(WEBHOOK);
        endpoint.setName("name");
        endpoint.setDescription("description");

        String responseBody = given()
                .basePath(API_INTEGRATIONS_V_1_0)
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

        featureFlipper.setEmailsOnlyMode(false);
    }

    @Test
    void testDeleteUnsupportedEndpointType() {
        featureFlipper.setEmailsOnlyMode(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("account-id", "org-id", "username");
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        when(endpointRepository.getEndpointTypeById(anyString(), any(UUID.class))).thenReturn(CAMEL);

        String responseBody = given()
                .basePath(API_INTEGRATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("id", UUID.randomUUID().toString())
                .when()
                .delete("/endpoints/{id}")
                .then()
                .statusCode(400)
                .extract().asString();
        assertEquals(UNSUPPORTED_ENDPOINT_TYPE, responseBody);

        featureFlipper.setEmailsOnlyMode(false);
    }

    @Test
    void testEnableUnsupportedEndpointType() {
        featureFlipper.setEmailsOnlyMode(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("account-id", "org-id", "username");
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        when(endpointRepository.getEndpointTypeById(anyString(), any(UUID.class))).thenReturn(CAMEL);

        String responseBody = given()
                .basePath(API_INTEGRATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("id", UUID.randomUUID().toString())
                .when()
                .put("/endpoints/{id}/enable")
                .then()
                .statusCode(400)
                .extract().asString();
        assertEquals(UNSUPPORTED_ENDPOINT_TYPE, responseBody);

        featureFlipper.setEmailsOnlyMode(false);
    }

    @Test
    void testDisableUnsupportedEndpointType() {
        featureFlipper.setEmailsOnlyMode(true);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("account-id", "org-id", "username");
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        when(endpointRepository.getEndpointTypeById(anyString(), any(UUID.class))).thenReturn(CAMEL);

        String responseBody = given()
                .basePath(API_INTEGRATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("id", UUID.randomUUID().toString())
                .when()
                .delete("/endpoints/{id}/enable")
                .then()
                .statusCode(400)
                .extract().asString();
        assertEquals(UNSUPPORTED_ENDPOINT_TYPE, responseBody);

        featureFlipper.setEmailsOnlyMode(false);
    }
}
