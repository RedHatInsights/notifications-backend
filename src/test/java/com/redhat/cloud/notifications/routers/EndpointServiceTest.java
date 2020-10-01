package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
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
import static org.hamcrest.CoreMatchers.equalTo;
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
                .statusCode(200) // TODO Maybe 204 here instead?
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

        Endpoint responsePoint = Json.decodeValue(response.getBody().asString(), Endpoint.class);
        assertNotNull(responsePoint.getId());

        // Fetch the list
        response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(200)
                .extract().response();

        List<Endpoint> endpoints = Json.decodeValue(response.getBody().asString(), List.class);
        assertEquals(1, endpoints.size());

        // Fetch single endpoint also and verify
        Endpoint responsePointSingle = fetchSingle(responsePoint.getId(), identityHeader);
        assertNotNull(responsePoint.getProperties());
        assertTrue(responsePointSingle.isEnabled());

        // Disable and fetch
        given()
                .header(identityHeader)
                .when().delete("/endpoints/" + responsePoint.getId() + "/enable")
                .then()
                .statusCode(200);

        responsePointSingle = fetchSingle(responsePoint.getId(), identityHeader);
        assertNotNull(responsePoint.getProperties());
        assertFalse(responsePointSingle.isEnabled());

        // Enable and fetch
        given()
                .header(identityHeader)
                .when().put("/endpoints/" + responsePoint.getId() + "/enable")
                .then()
                .statusCode(200);

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
                .statusCode(404);

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
                .body("id", equalTo(id.toString()))
                .extract().response();

        return Json.decodeValue(response.getBody().asString(), Endpoint.class);
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

    @Test
    void testEndpointValidation() {
        String tenant = "validation";
        String userName = "testEndpointValidation";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        // Add new endpoint without properties
        Endpoint ep = new Endpoint();
        ep.setType(Endpoint.EndpointType.WEBHOOK);
        ep.setName("endpoint with missing properties");
        ep.setDescription("Destined to fail");
        ep.setEnabled(true);

        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(400);

        WebhookAttributes webAttr = new WebhookAttributes();
        webAttr.setMethod(WebhookAttributes.HttpType.POST);
        webAttr.setDisableSSLVerification(false);
        webAttr.setSecretToken("my-super-secret-token");
        webAttr.setUrl(String.format("https://%s", mockServerConfig.getRunningAddress()));

        // Test with properties, but without endpoint type
        ep.setProperties(webAttr);
        ep.setType(null);

        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(400);

        // Test with incorrect webhook properties
        ep.setType(Endpoint.EndpointType.WEBHOOK);
        ep.setName("endpoint with incorrect webhook properties");
        webAttr.setMethod(null);

        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(400);

        // Type and attributes don't match
        webAttr.setMethod(WebhookAttributes.HttpType.POST);
        ep.setType(Endpoint.EndpointType.EMAIL);

        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(400);
    }

    @Test
    void testEndpointUpdates() {
        String tenant = "updates";
        String userName = "testEndpointUpdates";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        // Test empty tenant
        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(200) // TODO Maybe 204 here instead?
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

        Endpoint responsePoint = Json.decodeValue(response.getBody().asString(), Endpoint.class);
        assertNotNull(responsePoint.getId());

        // Fetch the list
        response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .contentType(ContentType.JSON)
                .when().get("/endpoints")
                .then()
                .statusCode(200)
                .extract().response();

        List<Endpoint> endpoints = Json.decodeValue(response.getBody().asString(), List.class);
        assertEquals(1, endpoints.size());

        // Fetch single endpoint also and verify
        Endpoint responsePointSingle = fetchSingle(responsePoint.getId(), identityHeader);
        assertNotNull(responsePoint.getProperties());
        assertTrue(responsePointSingle.isEnabled());

        // Update the endpoint
        responsePointSingle.setName("endpoint found");
        WebhookAttributes attrSingle = (WebhookAttributes) responsePointSingle.getProperties();
        attrSingle.setSecretToken("not-so-secret-anymore");

        // Update without payload
        given()
                .header(identityHeader)
                .contentType(ContentType.JSON)
                .when()
                .put(String.format("/endpoints/%s", responsePointSingle.getId()))
                .then()
                .statusCode(400);

        // With payload
        given()
                .header(identityHeader)
                .contentType(ContentType.JSON)
                .when()
                .body(Json.encode(responsePointSingle))
                .put(String.format("/endpoints/%s", responsePointSingle.getId()))
                .then()
                .statusCode(200);

        // Fetch single one again to see that the updates were done
        Endpoint updatedEndpoint = fetchSingle(responsePointSingle.getId(), identityHeader);
        WebhookAttributes attrSingleUpdated = (WebhookAttributes) updatedEndpoint.getProperties();
        assertEquals("endpoint found", updatedEndpoint.getName());
        assertEquals("not-so-secret-anymore", attrSingleUpdated.getSecretToken());
    }

    @Test
    void testEndpointLimiter() {
        String tenant = "limiter";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        for (int i = 0; i < 29; i++) {
            // Add new endpoints
            WebhookAttributes webAttr = new WebhookAttributes();
            webAttr.setMethod(WebhookAttributes.HttpType.POST);
            webAttr.setDisableSSLVerification(false);
            webAttr.setSecretToken("my-super-secret-token");
            webAttr.setUrl(String.format("https://%s/%d", mockServerConfig.getRunningAddress(), i));

            Endpoint ep = new Endpoint();
            ep.setType(Endpoint.EndpointType.WEBHOOK);
            ep.setName(String.format("Endpoint %d", i));
            ep.setDescription("Try to find me!");
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

            Endpoint responsePoint = Json.decodeValue(response.getBody().asString(), Endpoint.class);
            assertNotNull(responsePoint.getId());
        }

        // Fetch the list, page 1
        Response response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .queryParam("pageSize", "10")
                .queryParam("pageNumber", "0")
                .when().get("/endpoints")
                .then()
                .statusCode(200)
                .extract().response();

        List<Endpoint> endpoints = Json.decodeValue(response.getBody().asString(), List.class);
        assertEquals(10, endpoints.size());

        // Fetch the list, page 3
        response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .queryParam("pageSize", "10")
                .queryParam("pageNumber", "2")
                .when().get("/endpoints")
                .then()
                .statusCode(200)
                .extract().response();

        endpoints = Json.decodeValue(response.getBody().asString(), List.class);
        assertEquals(9, endpoints.size());
    }

    //    @Test
    void testConnectionCount() {
        String tenant = "count";
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
                .statusCode(200) // TODO Maybe 204 here instead?
                .body(is("[]"));

        for (int i = 0; i < 200; i++) {
            // Add new endpoints
            WebhookAttributes webAttr = new WebhookAttributes();
            webAttr.setMethod(WebhookAttributes.HttpType.POST);
            webAttr.setDisableSSLVerification(false);
            webAttr.setSecretToken("my-super-secret-token");
            webAttr.setUrl(String.format("https://%s", mockServerConfig.getRunningAddress()));

            Endpoint ep = new Endpoint();
            ep.setType(Endpoint.EndpointType.WEBHOOK);
            ep.setName("endpoint to find" + i);
            ep.setDescription("needle in the haystack" + i);
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

            Endpoint responsePoint = Json.decodeValue(response.getBody().asString(), Endpoint.class);
            assertNotNull(responsePoint.getId());

            // Fetch the list
            given()
                    // Set header to x-rh-identity
                    .header(identityHeader)
                    .when().get("/endpoints")
                    .then()
                    .statusCode(200)
                    .extract().response();
        }
    }
}
