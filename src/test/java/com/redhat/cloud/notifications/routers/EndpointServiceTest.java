package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.TEXT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EndpointServiceTest extends DbIsolatedTest {

    /*
     * In the tests below, most JSON responses are verified using JsonObject/JsonArray instead of deserializing these
     * responses into model instances and checking their attributes values. That's because the model classes contain
     * attributes annotated with @JsonProperty(access = READ_ONLY) which can't be deserialized and therefore verified
     * here. The deserialization is still performed only to verify that the JSON responses data structure is correct.
     */

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_1_0;
    }

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Inject
    ResourceHelpers helpers;

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
                .contentType(JSON)
                .body(is("{\"data\":[],\"links\":{},\"meta\":{\"count\":0}}"));

        // Add new endpoints
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(HttpType.POST);
        properties.setDisableSslVerification(false);
        properties.setSecretToken("my-super-secret-token");
        properties.setUrl(String.format("https://%s", mockServerConfig.getRunningAddress()));

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("endpoint to find");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);

        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(Endpoint.class);
        assertNotNull(responsePoint.getString("id"));

        // Fetch the list
        response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        List<Endpoint> endpoints = endpointPage.getData();
        assertEquals(1, endpoints.size());

        // Fetch single endpoint also and verify
        JsonObject responsePointSingle = fetchSingle(responsePoint.getString("id"), identityHeader);
        assertNotNull(responsePoint.getJsonObject("properties"));
        assertTrue(responsePointSingle.getBoolean("enabled"));

        // Disable and fetch
        String body =
                given()
                        .header(identityHeader)
                        .when().delete("/endpoints/" + responsePoint.getString("id") + "/enable")
                        .then()
                        .statusCode(204)
                        .extract().body().asString();
        assertEquals(0, body.length());

        responsePointSingle = fetchSingle(responsePoint.getString("id"), identityHeader);
        assertNotNull(responsePoint.getJsonObject("properties"));
        assertFalse(responsePointSingle.getBoolean("enabled"));

        // Enable and fetch
        given()
                .header(identityHeader)
                .when().put("/endpoints/" + responsePoint.getString("id") + "/enable")
                .then()
                .statusCode(200)
                .contentType(TEXT)
                .contentType(ContentType.TEXT);

        responsePointSingle = fetchSingle(responsePoint.getString("id"), identityHeader);
        assertNotNull(responsePoint.getJsonObject("properties"));
        assertTrue(responsePointSingle.getBoolean("enabled"));

        // Delete
        body =
                given()
                        .header(identityHeader)
                        .when().delete("/endpoints/" + responsePoint.getString("id"))
                        .then()
                        .statusCode(204)
                        .extract().body().asString();
        assertEquals(0, body.length());

        // Fetch single
        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints/" + responsePoint.getString("id"))
                .then()
                .statusCode(404)
                .contentType(JSON);

        // Fetch all, nothing should be left
        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body(is("{\"data\":[],\"links\":{},\"meta\":{\"count\":0}}"));
    }

    private JsonObject fetchSingle(String id, Header identityHeader) {
        Response response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints/" + id)
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("id", equalTo(id))
                .extract().response();

        JsonObject endpoint = new JsonObject(response.getBody().asString());
        endpoint.mapTo(Endpoint.class);
        return endpoint;
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
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("endpoint with missing properties");
        ep.setDescription("Destined to fail");
        ep.setEnabled(true);

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(400)
                .contentType(JSON);

        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(HttpType.POST);
        properties.setDisableSslVerification(false);
        properties.setSecretToken("my-super-secret-token");
        properties.setUrl(String.format("https://%s", mockServerConfig.getRunningAddress()));

        // Test with properties, but without endpoint type
        ep.setProperties(properties);
        ep.setType(null);

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(400);

        // Test with incorrect webhook properties
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("endpoint with incorrect webhook properties");
        properties.setMethod(null);

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(400)
                .contentType(JSON);

        // Type and attributes don't match
        properties.setMethod(HttpType.POST);
        ep.setType(EndpointType.EMAIL_SUBSCRIPTION);

        // FIXME Find a way to run the test below successfully.
        /*
         * The following test fails because of a bug which is not in our app.
         * The invalid properties should cause a deserialization error (see below) leading to an HTTP 400 response,
         * but the properties are deserialized as an instance of EmailSubscriptionProperties instead and we receive an HTTP 200 response.
         *
         * Expected error :
         * com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException:
         * Unrecognized field "url" (class com.redhat.cloud.notifications.models.EmailSubscriptionProperties), not marked as ignorable (0 known properties: ])
         * at [Source: (String)"{"id":null,"name":"endpoint with incorrect webhook properties","description":"Destined to fail","enabled":true,"type":"email_subscription","created":null,"updated":null,"properties":{"url":"https://localhost:49368","method":"POST","disable_ssl_verification":false,"secret_token":"my-super-secret-token","basic_authentication":null}}"; line: 1, column: 332] (through reference chain: com.redhat.cloud.notifications.models.EmailSubscriptionProperties["url"])
         * at com.redhat.cloud.notifications.routers.EndpointServiceTest.testEndpointValidation(EndpointServiceTest.java:257)
         *
         * This might be a Quarkus issue, investigation in progress...
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(400);
         */
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
                .contentType(JSON)
                .body(is("{\"data\":[],\"links\":{},\"meta\":{\"count\":0}}"));

        // Add new endpoints
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(HttpType.POST);
        properties.setDisableSslVerification(false);
        properties.setSecretToken("my-super-secret-token");
        properties.setUrl(String.format("https://%s", mockServerConfig.getRunningAddress()));

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("endpoint to find");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);

        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(Endpoint.class);
        assertNotNull(responsePoint.getString("id"));

        // Fetch the list
        response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .contentType(JSON)
                .when().get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        List<Endpoint> endpoints = endpointPage.getData();
        assertEquals(1, endpoints.size());

        // Fetch single endpoint also and verify
        JsonObject responsePointSingle = fetchSingle(responsePoint.getString("id"), identityHeader);
        assertNotNull(responsePoint.getJsonObject("properties"));
        assertTrue(responsePointSingle.getBoolean("enabled"));

        // Update the endpoint
        responsePointSingle.put("name", "endpoint found");
        JsonObject attrSingle = responsePointSingle.getJsonObject("properties");
        attrSingle.mapTo(WebhookProperties.class);
        attrSingle.put("secret_token", "not-so-secret-anymore");

        // Update without payload
        given()
                .header(identityHeader)
                .contentType(JSON)
                .when()
                .put(String.format("/endpoints/%s", responsePointSingle.getString("id")))
                .then()
                .statusCode(400);

        // With payload
        given()
                .header(identityHeader)
                .contentType(JSON)
                .when()
                .body(Json.encode(responsePointSingle))
                .put(String.format("/endpoints/%s", responsePointSingle.getString("id")))
                .then()
                .statusCode(200)
                .contentType(TEXT);

        // Fetch single one again to see that the updates were done
        JsonObject updatedEndpoint = fetchSingle(responsePointSingle.getString("id"), identityHeader);
        JsonObject attrSingleUpdated = updatedEndpoint.getJsonObject("properties");
        attrSingleUpdated.mapTo(WebhookProperties.class);
        assertEquals("endpoint found", updatedEndpoint.getString("name"));
        assertEquals("not-so-secret-anymore", attrSingleUpdated.getString("secret_token"));
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
            WebhookProperties properties = new WebhookProperties();
            properties.setMethod(HttpType.POST);
            properties.setDisableSslVerification(false);
            properties.setSecretToken("my-super-secret-token");
            properties.setUrl(String.format("https://%s/%d", mockServerConfig.getRunningAddress(), i));

            Endpoint ep = new Endpoint();
            ep.setType(EndpointType.WEBHOOK);
            ep.setName(String.format("Endpoint %d", i));
            ep.setDescription("Try to find me!");
            ep.setEnabled(true);
            ep.setProperties(properties);

            Response response = given()
                    .header(identityHeader)
                    .when()
                    .contentType(JSON)
                    .body(Json.encode(ep))
                    .post("/endpoints")
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .extract().response();

            JsonObject responsePoint = new JsonObject(response.getBody().asString());
            responsePoint.mapTo(Endpoint.class);
            assertNotNull(responsePoint.getString("id"));
        }

        // Fetch the list, page 1
        Response response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .queryParam("limit", "10")
                .queryParam("offset", "0")
                .when().get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        List<Endpoint> endpoints = endpointPage.getData();
        assertEquals(10, endpoints.size());
        assertEquals(29, endpointPage.getMeta().getCount());

        // Fetch the list, page 3
        response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .queryParam("limit", "10")
                .queryParam("pageNumber", "2")
                .when().get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        endpoints = endpointPage.getData();
        assertEquals(9, endpoints.size());
        assertEquals(29, endpointPage.getMeta().getCount());
    }

    // TODO [BG Phase 2] Delete this test
    @Test
    void testDefaultEndpointRegistering() {
        String tenant = "defaultRegister";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.DEFAULT);
        ep.setName("Default endpoint");
        ep.setDescription("The ultimate fallback");
        ep.setEnabled(true);

        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(Endpoint.class);
        assertNotNull(responsePoint.getString("id"));

        JsonObject responsePointSingle = fetchSingle(responsePoint.getString("id"), identityHeader);
        assertEquals(responsePoint.getString("id"), responsePointSingle.getString("id"));
        assertEquals(responsePoint.getString("type"), responsePointSingle.getString("type"));

        // Fetch the default endpoint
        response = given()
                .header(identityHeader)
                .queryParam("type", "default")
                .when()
                .contentType(JSON)
                .get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonObject endpointPage = new JsonObject(response.getBody().asString());
        endpointPage.mapTo(EndpointPage.class);
        assertEquals(1, endpointPage.getJsonArray("data").size());
        assertEquals(1, endpointPage.getJsonObject("meta").getLong("count"));
        assertEquals(responsePoint.getString("id"), endpointPage.getJsonArray("data").getJsonObject(0).getString("id"));
        assertEquals(EndpointType.DEFAULT.name().toLowerCase(), responsePoint.getString("type"));

        // Add another type as well
        Endpoint another = new Endpoint();
        another.setType(EndpointType.WEBHOOK);
        another.setDescription("desc");
        another.setName("name");

        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(HttpType.POST);
        properties.setUrl("http://localhost");

        another.setProperties(properties);

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(another))
                .post("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON);

        // Ensure that there's only a single default endpoint
        // This second insert should return the original one without modifications
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON);

        response = given()
                .header(identityHeader)
                .queryParam("type", "default")
                .when()
                .get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        endpointPage = new JsonObject(response.getBody().asString());
        endpointPage.mapTo(EndpointPage.class);
        assertEquals(1, endpointPage.getJsonArray("data").size());
        assertEquals(responsePoint.getString("id"), endpointPage.getJsonArray("data").getJsonObject(0).getString("id"));
        assertEquals(EndpointType.DEFAULT.name().toLowerCase(), responsePoint.getString("type"));
    }

    @Test
    void testSortingOrder() {
        String tenant = "testSortingOrder";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        // Create 50 test-ones with sanely sortable name & enabled & disabled & type
        int[] stats = helpers.createTestEndpoints(tenant, 50);
        int disableCount = stats[1];
        int webhookCount = stats[2];

        Response response = given()
                .header(identityHeader)
                .when()
                .get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        Endpoint[] endpoints = endpointPage.getData().toArray(new Endpoint[0]);
        assertEquals(stats[0], endpoints.length);

        response = given()
                .header(identityHeader)
                .queryParam("sort_by", "enabled")
                .when()
                .get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        endpoints = endpointPage.getData().toArray(new Endpoint[0]);
        assertFalse(endpoints[0].isEnabled());
        assertFalse(endpoints[disableCount - 1].isEnabled());
        assertTrue(endpoints[disableCount].isEnabled());
        assertTrue(endpoints[stats[0] - 1].isEnabled());

        response = given()
                .header(identityHeader)
                .queryParam("sort_by", "name:desc")
                .queryParam("limit", "50")
                .queryParam("offset", stats[0] - 20)
                .when()
                .get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        endpoints = endpointPage.getData().toArray(new Endpoint[0]);
        assertEquals(20, endpoints.length);
        // TODO [BG Phase 2] Delete the 3 following lines
        assertEquals("Default endpoint", endpoints[endpoints.length - 1].getName());
        assertEquals("Endpoint 1", endpoints[endpoints.length - 2].getName());
        assertEquals("Endpoint 26", endpoints[0].getName());
        // TODO [BG Phase 2] Uncomment this:
        /*
        assertEquals("Endpoint 1", endpoints[endpoints.length - 1].getName());
        assertEquals("Endpoint 10", endpoints[endpoints.length - 2].getName());
        assertEquals("Endpoint 27", endpoints[0].getName());
        */
    }

    @Test
    void testWebhookAttributes() {
        String tenant = "testWebhookAttributes";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        // Add new endpoints
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(HttpType.POST);
        properties.setDisableSslVerification(false);
        properties.setSecretToken("my-super-secret-token");
        properties.setBasicAuthentication(new BasicAuthentication("myuser", "mypassword"));
        properties.setUrl(String.format("https://%s", mockServerConfig.getRunningAddress()));

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("endpoint to find");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);

        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(Endpoint.class);
        assertNotNull(responsePoint.getString("id"));

        // Fetch single endpoint also and verify
        JsonObject responsePointSingle = fetchSingle(responsePoint.getString("id"), identityHeader);

        assertNotNull(responsePoint.getJsonObject("properties"));
        assertTrue(responsePointSingle.getBoolean("enabled"));
        assertNotNull(responsePointSingle.getJsonObject("properties"));
        assertNotNull(responsePointSingle.getJsonObject("properties").getString("secret_token"));

        JsonObject attr = responsePointSingle.getJsonObject("properties");
        attr.mapTo(WebhookProperties.class);
        assertNotNull(attr.getJsonObject("basic_authentication"));
        assertEquals("mypassword", attr.getJsonObject("basic_authentication").getString("password"));
    }

    @Test
    void testAddEndpointEmailSubscription() {
        String tenant = "adding-email-subscription";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        // Add new EmailSubscriptionEndpoint
        EmailSubscriptionProperties properties = new EmailSubscriptionProperties();

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.EMAIL_SUBSCRIPTION);
        ep.setName("Endpoint: EmailSubscription");
        ep.setDescription("Subscribe!");
        ep.setEnabled(true);
        ep.setProperties(properties);

        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(Endpoint.class);
        assertNotNull(responsePoint.getString("id"));

        // Delete
        String body =
                given()
                        .header(identityHeader)
                        .when().delete("/endpoints/" + responsePoint.getString("id"))
                        .then()
                        .statusCode(204)
                        .extract().body().asString();
        assertEquals(0, body.length());
    }

    @Test
    void testEmailSubscription() {
        String tenant = "test-subscription";
        String username = "test-user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, username);
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);
        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        this.helpers.createTestAppAndEventTypes();

        // invalid bundle/application combination gives a 404
        given()
                .header(identityHeader)
                .when()
                .delete("/endpoints/email/subscription/rhel/" + ResourceHelpers.TEST_APP_NAME + "/instant")
                .then().statusCode(404)
                .contentType(JSON);
        given()
                .header(identityHeader)
                .when()
                .delete("/endpoints/email/subscription/" + ResourceHelpers.TEST_BUNDLE_NAME + "/policies/instant")
                .then().statusCode(404)
                .contentType(JSON);
        given()
                .header(identityHeader)
                .when()
                .put("/endpoints/email/subscription/rhel/" + ResourceHelpers.TEST_APP_NAME + "/instant")
                .then().statusCode(404)
                .contentType(JSON);
        given()
                .header(identityHeader)
                .when()
                .put("/endpoints/email/subscription/" + ResourceHelpers.TEST_BUNDLE_NAME + "/policies/instant")
                .then().statusCode(404)
                .contentType(JSON);

        // Unknown bundle/apps give 404
        given()
                .header(identityHeader)
                .when()
                .delete("/endpoints/email/subscription/idontexist/meneither/instant")
                .then().statusCode(404)
                .contentType(JSON);
        given()
                .header(identityHeader)
                .when()
                .put("/endpoints/email/subscription/idontexist/meneither/instant")
                .then().statusCode(404)
                .contentType(JSON);

        // Disable everything as preparation
        // rhel/policies instant and daily
        // TEST_BUNDLE_NAME/TEST_APP_NAME instant and daily
        given()
                .header(identityHeader)
                .when()
                .delete("/endpoints/email/subscription/rhel/policies/instant")
                .then().statusCode(200)
                .contentType(JSON);
        given()
                .header(identityHeader)
                .when()
                .delete("/endpoints/email/subscription/" + ResourceHelpers.TEST_BUNDLE_NAME + "/" + ResourceHelpers.TEST_APP_NAME + "/instant")
                .then().statusCode(200)
                .contentType(JSON);
        given()
                .header(identityHeader)
                .when()
                .delete("/endpoints/email/subscription/rhel/policies/daily")
                .then().statusCode(200)
                .contentType(JSON);
        given()
                .header(identityHeader)
                .when()
                .delete("/endpoints/email/subscription/" + ResourceHelpers.TEST_BUNDLE_NAME + "/" + ResourceHelpers.TEST_APP_NAME + "/daily")
                .then().statusCode(200)
                .contentType(JSON);

        assertNull(this.helpers.getSubscription(tenant, username, "rhel", "policies", EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, "rhel", "policies", EmailSubscriptionType.DAILY));
        assertNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.DAILY));

        // Enable instant on rhel.policies
        given()
                .header(identityHeader)
                .when()
                .put("/endpoints/email/subscription/rhel/policies/instant")
                .then().statusCode(200).contentType(JSON);

        assertNotNull(this.helpers.getSubscription(tenant, username, "rhel", "policies", EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, "rhel", "policies", EmailSubscriptionType.DAILY));
        assertNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.DAILY));

        // Enable daily on rhel.policies
        given()
                .header(identityHeader)
                .when()
                .put("/endpoints/email/subscription/rhel/policies/daily")
                .then().statusCode(200).contentType(JSON);

        assertNotNull(this.helpers.getSubscription(tenant, username, "rhel", "policies", EmailSubscriptionType.INSTANT));
        assertNotNull(this.helpers.getSubscription(tenant, username, "rhel", "policies", EmailSubscriptionType.DAILY));
        assertNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.DAILY));

        // Enable instant on TEST_BUNDLE_NAME.TEST_APP_NAME
        given()
                .header(identityHeader)
                .when()
                .put("/endpoints/email/subscription/" + ResourceHelpers.TEST_BUNDLE_NAME + "/" + ResourceHelpers.TEST_APP_NAME + "/instant")
                .then().statusCode(200).contentType(JSON);

        assertNotNull(this.helpers.getSubscription(tenant, username, "rhel", "policies", EmailSubscriptionType.INSTANT));
        assertNotNull(this.helpers.getSubscription(tenant, username, "rhel", "policies", EmailSubscriptionType.DAILY));
        assertNotNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.DAILY));

        // Disable daily on rhel.policies
        given()
                .header(identityHeader)
                .when()
                .delete("/endpoints/email/subscription/rhel/policies/daily")
                .then().statusCode(200).contentType(JSON);

        assertNotNull(this.helpers.getSubscription(tenant, username, "rhel", "policies", EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, "rhel", "policies", EmailSubscriptionType.DAILY));
        assertNotNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.DAILY));

        // Disable instant on rhel.policies
        given()
                .header(identityHeader)
                .when()
                .delete("/endpoints/email/subscription/rhel/policies/instant")
                .then().statusCode(200).contentType(JSON);

        assertNull(this.helpers.getSubscription(tenant, username, "rhel", "policies", EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, "rhel", "policies", EmailSubscriptionType.DAILY));
        assertNotNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.DAILY));
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
                .contentType(JSON)
                .body(is("{\"data\":[],\"links\":{},\"meta\":{\"count\":0}}"));

        for (int i = 0; i < 200; i++) {
            // Add new endpoints
            WebhookProperties properties = new WebhookProperties();
            properties.setMethod(HttpType.POST);
            properties.setDisableSslVerification(false);
            properties.setSecretToken("my-super-secret-token");
            properties.setUrl(String.format("https://%s", mockServerConfig.getRunningAddress()));

            Endpoint ep = new Endpoint();
            ep.setType(EndpointType.WEBHOOK);
            ep.setName("endpoint to find" + i);
            ep.setDescription("needle in the haystack" + i);
            ep.setEnabled(true);
            ep.setProperties(properties);

            Response response = given()
                    .header(identityHeader)
                    .when()
                    .contentType(JSON)
                    .body(Json.encode(ep))
                    .post("/endpoints")
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .extract().response();

            JsonObject responsePoint = new JsonObject(response.getBody().asString());
            responsePoint.mapTo(Endpoint.class);
            assertNotNull(responsePoint.getString("id"));

            // Fetch the list
            given()
                    // Set header to x-rh-identity
                    .header(identityHeader)
                    .when().get("/endpoints")
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .extract().response();
        }
    }
}
