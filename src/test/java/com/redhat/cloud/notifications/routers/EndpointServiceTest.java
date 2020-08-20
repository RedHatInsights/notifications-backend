package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.json.Json;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EndpointServiceTest {

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Test
    void testEndpointAdding() {
        String tenant = "empty";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        // Test empty tenant
        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(200)
                .body(is("[]"));

        // Add new endpoints
        WebhookAttributes webAttr = new WebhookAttributes();
        webAttr.setMethod(WebhookAttributes.HttpType.POST);
        webAttr.setDisableSSLVerification(false);
        webAttr.setSecretToken("my-super-secret-token");
        webAttr.setUrl(String.format("https://%s", mockServerConfig.getRunningAddress()));

        Endpoint ep = new Endpoint();
        ep.setType(Endpoint.EndpointType.WEBHOOK);
        ep.setName("endpoint to find");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(webAttr);

        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(200)
                .extract().response();

        // Should this be a list?
        Endpoint responsePoint = Json.decodeValue(response.getBody().asString(), Endpoint.class);
        assertNotNull(responsePoint.getId());

        // Fetch single endpoint and endpoints
        Endpoint responsePointSingle = fetchSingle(responsePoint.getId(), identityHeader);
        assertNotNull(responsePoint.getProperties());
        assertTrue(responsePointSingle.isEnabled());

        // Disable and fetch
        given()
                .header(identityHeader)
                .when().delete("/endpoints/" + responsePoint.getId() + "/enable")
                .then()
                .statusCode(204);

        responsePointSingle = fetchSingle(responsePoint.getId(), identityHeader);
        assertNotNull(responsePoint.getProperties());
        assertFalse(responsePointSingle.isEnabled());

        // Enable and fetch
        given()
                .header(identityHeader)
                .when().put("/endpoints/" + responsePoint.getId() + "/enable")
                .then()
                .statusCode(204);

        responsePointSingle = fetchSingle(responsePoint.getId(), identityHeader);
        assertNotNull(responsePoint.getProperties());
        assertTrue(responsePointSingle.isEnabled());

        // Delete
        given()
                .header(identityHeader)
                .when().delete("/endpoints/" + responsePoint.getId())
                .then()
                .statusCode(200);

        // Fetch single
        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints/" + responsePoint.getId())
                .then()
                .statusCode(204); // TODO This should return 404 and not 204

        // Fetch all, nothing should be left
        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(200)
                .body(is("[]"));
    }

    private Endpoint fetchSingle(UUID id, Header identityHeader) {
        Response response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints/" + id)
                .then()
                .statusCode(200)
                .extract().response();

        Endpoint responsePointSingle = Json.decodeValue(response.getBody().asString(), Endpoint.class);
        assertEquals(id, responsePointSingle.getId());
        return responsePointSingle;
    }

    @Test
    void testEndpointRoles() {
        String tenant = "empty";
        String userName = "testEndpointRoles";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        // Fetch endpoint without any Rbac details - errors cause 401
        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(401);

        // Fetch endpoint with no access - Rbac succeed returns 403
        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.NO_ACCESS);

        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(403);

        // Test bogus x-rh-identity header that fails Base64 decoding
        given()
                .header(new Header("x-rh-identity", "00000"))
                .when().get("/endpoints")
                .then()
                .statusCode(401);
    }
}
