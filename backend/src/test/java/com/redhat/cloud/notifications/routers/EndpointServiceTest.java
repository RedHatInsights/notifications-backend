package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import com.redhat.cloud.notifications.routers.models.RequestEmailSubscriptionProperties;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.smallrye.mutiny.vertx.MutinyHelper;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.redhat.cloud.notifications.TestThreadHelper.runOnWorkerThread;
import static com.redhat.cloud.notifications.db.ResourceHelpers.TEST_APP_NAME;
import static com.redhat.cloud.notifications.db.ResourceHelpers.TEST_BUNDLE_NAME;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.TEXT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Inject
    EndpointEmailSubscriptionResources subscriptionResources;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Inject
    Vertx vertx;

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
    }

    @Test
    void addCamelEndpoint() {

        String tenant = "empty";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        CamelProperties cAttr = new CamelProperties();
        cAttr.setDisableSslVerification(false);
        cAttr.setUrl(String.format("https://%s", mockServerConfig.getRunningAddress()));
        cAttr.setSubType("ansible");
        cAttr.setBasicAuthentication(new BasicAuthentication("testuser", "secret"));
        Map<String, String> extras = new HashMap<>();
        extras.put("template", "11");
        cAttr.setExtras(extras);

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.CAMEL);
        ep.setName("Push the camel through the needle's ear");
        ep.setDescription("How many humps has a camel?");
        ep.setEnabled(true);
        ep.setProperties(cAttr);

        String responseBody = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().asString();

        JsonObject responsePoint = new JsonObject(responseBody);
        responsePoint.mapTo(Endpoint.class);
        String id = responsePoint.getString("id");
        assertNotNull(id);

        try {
            JsonObject endpoint = fetchSingle(id, identityHeader);
            JsonObject properties = responsePoint.getJsonObject("properties");
            assertNotNull(properties);
            assertTrue(endpoint.getBoolean("enabled"));
            assertEquals("ansible", properties.getString("sub_type"));
            JsonObject extrasObject = properties.getJsonObject("extras");
            assertNotNull(extrasObject);
            String template  = extrasObject.getString("template");
            assertEquals("11", template);

            JsonObject basicAuth = properties.getJsonObject("basic_authentication");
            assertNotNull(basicAuth);
            String user = basicAuth.getString("username");
            String pass = basicAuth.getString("password");
            assertEquals("testuser", user);
            assertEquals("secret", pass);

        } finally {

            given()
                    .header(identityHeader)
                    .when().delete("/endpoints/" + id)
                    .then()
                    .statusCode(204)
                    .extract().body().asString();
        }
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
                .body(responsePointSingle.encode())
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

    @Test
    void testSortingOrder() {
        String tenant = "testSortingOrder";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        // Create 50 test-ones with sanely sortable name & enabled & disabled & type
        sessionFactory.withSession(session -> helpers.createTestEndpoints(tenant, 50)
                .call(stats -> runOnWorkerThread(() -> {
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
                    assertEquals("Endpoint 1", endpoints[endpoints.length - 1].getName());
                    assertEquals("Endpoint 10", endpoints[endpoints.length - 2].getName());
                    assertEquals("Endpoint 27", endpoints[0].getName());
                }).get())
        ).await().indefinitely();
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

        // EmailSubscription can't be created
        EmailSubscriptionProperties properties = new EmailSubscriptionProperties();

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.EMAIL_SUBSCRIPTION);
        ep.setName("Endpoint: EmailSubscription");
        ep.setDescription("Subscribe!");
        ep.setEnabled(true);
        ep.setProperties(properties);

        String stringResponse = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(400)
                .extract().asString();

        assertSystemEndpointTypeError(stringResponse, EndpointType.EMAIL_SUBSCRIPTION);

        RequestEmailSubscriptionProperties requestProps = new RequestEmailSubscriptionProperties();

        // EmailSubscription can be fetch from the properties
        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(requestProps))
                .post("/endpoints/system/email_subscription")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(Endpoint.class);
        assertNotNull(responsePoint.getString("id"));

        // It is always enabled
        assertEquals(true, responsePoint.getBoolean("enabled"));

        // Calling again yields the same endpoint id
        String defaultEndpointId = responsePoint.getString("id");

        response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(requestProps))
                .post("/endpoints/system/email_subscription")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(Endpoint.class);
        assertEquals(defaultEndpointId, responsePoint.getString("id"));

        // Different properties are different endpoints
        Set<String> endpointIds = new HashSet<>();
        endpointIds.add(defaultEndpointId);

        requestProps.setOnlyAdmins(true);

        response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(requestProps))
                .post("/endpoints/system/email_subscription")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(Endpoint.class);
        assertFalse(endpointIds.contains(responsePoint.getString("id")));
        endpointIds.add(responsePoint.getString("id"));

        response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(requestProps))
                .post("/endpoints/system/email_subscription")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(Endpoint.class);
        assertTrue(endpointIds.contains(responsePoint.getString("id")));

        // It is not possible to delete it
        stringResponse = given()
                .header(identityHeader)
                .when().delete("/endpoints/" + defaultEndpointId)
                .then()
                .statusCode(400)
                .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.EMAIL_SUBSCRIPTION);

        // It is not possible to disable or enable it
        stringResponse = given()
                .header(identityHeader)
                .when().delete("/endpoints/" + defaultEndpointId + "/enable")
                .then()
                .statusCode(400)
                .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.EMAIL_SUBSCRIPTION);

        stringResponse = given()
                .header(identityHeader)
                .when().put("/endpoints/" + defaultEndpointId + "/enable")
                .then()
                .statusCode(400)
                .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.EMAIL_SUBSCRIPTION);

        // It is not possible to update it
        stringResponse = given()
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(ep))
                .when().put("/endpoints/" + defaultEndpointId)
                .then()
                .statusCode(400)
                .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.EMAIL_SUBSCRIPTION);

        // It is not possible to update it to other type
        ep.setType(EndpointType.WEBHOOK);

        WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setMethod(HttpType.POST);
        webhookProperties.setDisableSslVerification(false);
        webhookProperties.setSecretToken("my-super-secret-token");
        webhookProperties.setUrl(String.format("https://%s", mockServerConfig.getRunningAddress()));
        ep.setProperties(webhookProperties);

        stringResponse = given()
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(ep))
                .when().put("/endpoints/" + defaultEndpointId)
                .then()
                .statusCode(400)
                .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.EMAIL_SUBSCRIPTION);
    }

    @Test
    void testEmailSubscription() {
        String tenant = "test-subscription";
        String username = "test-user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, username);
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);
        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        sessionFactory.withSession(session -> helpers.createTestAppAndEventTypes()
                .chain(runOnWorkerThread(() -> {
                    // invalid bundle/application combination gives a 404
                    given()
                            .header(identityHeader)
                            .when()
                            .delete("/endpoints/email/subscription/rhel/" + TEST_APP_NAME + "/instant")
                            .then().statusCode(404)
                            .contentType(JSON);
                    given()
                            .header(identityHeader)
                            .when()
                            .delete("/endpoints/email/subscription/" + TEST_BUNDLE_NAME + "/policies/instant")
                            .then().statusCode(404)
                            .contentType(JSON);
                    given()
                            .header(identityHeader)
                            .when()
                            .put("/endpoints/email/subscription/rhel/" + TEST_APP_NAME + "/instant")
                            .then().statusCode(404)
                            .contentType(JSON);
                    given()
                            .header(identityHeader)
                            .when()
                            .put("/endpoints/email/subscription/" + TEST_BUNDLE_NAME + "/policies/instant")
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
                            .delete("/endpoints/email/subscription/" + TEST_BUNDLE_NAME + "/" + TEST_APP_NAME + "/instant")
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
                            .delete("/endpoints/email/subscription/" + TEST_BUNDLE_NAME + "/" + TEST_APP_NAME + "/daily")
                            .then().statusCode(200)
                            .contentType(JSON);
                }))
                // The stream is currently emitting items from a worker thread. We need to switch back to the event loop thread for Hibernate Reactive.
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, "rhel", "policies", INSTANT))
                .invoke(Assertions::assertNull)
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, "rhel", "policies", DAILY))
                .invoke(Assertions::assertNull)
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, TEST_BUNDLE_NAME, TEST_APP_NAME, INSTANT))
                .invoke(Assertions::assertNull)
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, TEST_BUNDLE_NAME, TEST_APP_NAME, DAILY))
                .invoke(Assertions::assertNull)
                .chain(runOnWorkerThread(() -> {
                    // Enable instant on rhel.policies
                    given()
                            .header(identityHeader)
                            .when()
                            .put("/endpoints/email/subscription/rhel/policies/instant")
                            .then().statusCode(200).contentType(JSON);
                }))
                // The stream is currently emitting items from a worker thread. We need to switch back to the event loop thread for Hibernate Reactive.
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, "rhel", "policies", INSTANT))
                .invoke(Assertions::assertNotNull)
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, "rhel", "policies", DAILY))
                .invoke(Assertions::assertNull)
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, TEST_BUNDLE_NAME, TEST_APP_NAME, INSTANT))
                .invoke(Assertions::assertNull)
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, TEST_BUNDLE_NAME, TEST_APP_NAME, DAILY))
                .invoke(Assertions::assertNull)
                .chain(runOnWorkerThread(() -> {
                    // Enable daily on rhel.policies
                    given()
                            .header(identityHeader)
                            .when()
                            .put("/endpoints/email/subscription/rhel/policies/daily")
                            .then().statusCode(200).contentType(JSON);
                }))
                // The stream is currently emitting items from a worker thread. We need to switch back to the event loop thread for Hibernate Reactive.
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, "rhel", "policies", INSTANT))
                .invoke(Assertions::assertNotNull)
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, "rhel", "policies", DAILY))
                .invoke(Assertions::assertNotNull)
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, TEST_BUNDLE_NAME, TEST_APP_NAME, INSTANT))
                .invoke(Assertions::assertNull)
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, TEST_BUNDLE_NAME, TEST_APP_NAME, DAILY))
                .invoke(Assertions::assertNull)
                .chain(runOnWorkerThread(() -> {
                    // Enable instant on TEST_BUNDLE_NAME.TEST_APP_NAME
                    given()
                            .header(identityHeader)
                            .when()
                            .put("/endpoints/email/subscription/" + TEST_BUNDLE_NAME + "/" + TEST_APP_NAME + "/instant")
                            .then().statusCode(200).contentType(JSON);
                }))
                // The stream is currently emitting items from a worker thread. We need to switch back to the event loop thread for Hibernate Reactive.
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, "rhel", "policies", INSTANT))
                .invoke(Assertions::assertNotNull)
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, "rhel", "policies", DAILY))
                .invoke(Assertions::assertNotNull)
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, TEST_BUNDLE_NAME, TEST_APP_NAME, INSTANT))
                .invoke(Assertions::assertNotNull)
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, TEST_BUNDLE_NAME, TEST_APP_NAME, DAILY))
                .invoke(Assertions::assertNull)
                .chain(runOnWorkerThread(() -> {
                    // Disable daily on rhel.policies
                    given()
                            .header(identityHeader)
                            .when()
                            .delete("/endpoints/email/subscription/rhel/policies/daily")
                            .then().statusCode(200).contentType(JSON);
                }))
                // The stream is currently emitting items from a worker thread. We need to switch back to the event loop thread for Hibernate Reactive.
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, "rhel", "policies", INSTANT))
                .invoke(Assertions::assertNotNull)
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, "rhel", "policies", DAILY))
                .invoke(Assertions::assertNull)
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, TEST_BUNDLE_NAME, TEST_APP_NAME, INSTANT))
                .invoke(Assertions::assertNotNull)
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, TEST_BUNDLE_NAME, TEST_APP_NAME, DAILY))
                .invoke(Assertions::assertNull)
                .chain(runOnWorkerThread(() -> {
                    // Disable instant on rhel.policies
                    given()
                            .header(identityHeader)
                            .when()
                            .delete("/endpoints/email/subscription/rhel/policies/instant")
                            .then().statusCode(200).contentType(JSON);
                }))
                // The stream is currently emitting items from a worker thread. We need to switch back to the event loop thread for Hibernate Reactive.
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, "rhel", "policies", INSTANT))
                .invoke(Assertions::assertNull)
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, "rhel", "policies", DAILY))
                .invoke(Assertions::assertNull)
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, TEST_BUNDLE_NAME, TEST_APP_NAME, INSTANT))
                .invoke(Assertions::assertNotNull)
                .chain(() -> subscriptionResources.getEmailSubscription(tenant, username, TEST_BUNDLE_NAME, TEST_APP_NAME, DAILY))
                .invoke(Assertions::assertNull)
        ).await().indefinitely();
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

    private void assertSystemEndpointTypeError(String message, EndpointType endpointType) {
        assertTrue(message.contains(String.format(
                "Is not possible to create or alter endpoint with type %s, check API for alternatives",
                endpointType
        )));
    }

}
