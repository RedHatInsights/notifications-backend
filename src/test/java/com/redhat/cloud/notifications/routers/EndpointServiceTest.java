package com.redhat.cloud.notifications.routers;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.EmailSubscription.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.EmailSubscriptionAttributes;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Endpoint.EndpointType;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.json.Json;
import io.vertx.core.json.jackson.DatabindCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EndpointServiceTest {

    @BeforeAll
    static void beforeAll() {
        DatabindCodec.mapper().registerModule(new JavaTimeModule());
    }

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
                .body(is("{\"data\":[],\"links\":{},\"meta\":{\"count\":0}}"));

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

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        List<Endpoint> endpoints = endpointPage.getData();
        assertEquals(1, endpoints.size());

        // Fetch single endpoint also and verify
        Endpoint responsePointSingle = fetchSingle(responsePoint.getId(), identityHeader);
        assertNotNull(responsePoint.getProperties());
        assertTrue(responsePointSingle.isEnabled());

        // Disable and fetch
        String body =
            given()
                .header(identityHeader)
                .when().delete("/endpoints/" + responsePoint.getId() + "/enable")
                .then()
                .statusCode(204)
                .extract().body().asString();
        assertEquals(0, body.length());

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
        body =
            given()
                .header(identityHeader)
                .when().delete("/endpoints/" + responsePoint.getId())
                .then()
                .statusCode(204)
                .extract().body().asString();
        assertEquals(0, body.length());

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
                .body(is("{\"data\":[],\"links\":{},\"meta\":{\"count\":0}}"));
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
        ep.setType(EndpointType.EMAIL_SUBSCRIPTION);

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
                .body(is("{\"data\":[],\"links\":{},\"meta\":{\"count\":0}}"));

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

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        List<Endpoint> endpoints = endpointPage.getData();
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
                .queryParam("limit", "10")
                .queryParam("offset", "0")
                .when().get("/endpoints")
                .then()
                .statusCode(200)
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
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        endpoints = endpointPage.getData();
        assertEquals(9, endpoints.size());
        assertEquals(29, endpointPage.getMeta().getCount());
    }

    @Test
    void testDefaultEndpointRegistering() {
        String tenant = "defaultRegister";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        Endpoint ep = new Endpoint();
        ep.setType(Endpoint.EndpointType.DEFAULT);
        ep.setName("Default endpoint");
        ep.setDescription("The ultimate fallback");
        ep.setEnabled(true);

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

        Endpoint responsePointSingle = fetchSingle(responsePoint.getId(), identityHeader);
        assertEquals(responsePoint.getId(), responsePointSingle.getId());
        assertEquals(responsePoint.getType(), responsePointSingle.getType());

        // Fetch the default endpoint
        response = given()
                .header(identityHeader)
                .queryParam("type", "default")
                .when()
                .contentType(ContentType.JSON)
                .get("/endpoints")
                .then()
                .statusCode(200)
                .extract().response();

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        assertEquals(1, endpointPage.getData().size());
        assertEquals(1, endpointPage.getMeta().getCount());
        assertEquals(responsePoint.getId(), endpointPage.getData().get(0).getId());
        assertEquals(Endpoint.EndpointType.DEFAULT, responsePoint.getType());

        // Add another type as well
        Endpoint another = new Endpoint();
        another.setType(Endpoint.EndpointType.WEBHOOK);
        another.setDescription("desc");
        another.setName("name");

        WebhookAttributes attr = new WebhookAttributes();
        attr.setMethod(WebhookAttributes.HttpType.POST);
        attr.setUrl("http://localhost");

        another.setProperties(attr);

        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(another))
                .post("/endpoints")
                .then()
                .statusCode(200);

        // Ensure that there's only a single default endpoint
        // This second insert should return the original one without modifications
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(200);

        response = given()
                .header(identityHeader)
                .queryParam("type", "default")
                .when()
                .contentType(ContentType.JSON)
                .get("/endpoints")
                .then()
                .statusCode(200)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        assertEquals(1, endpointPage.getData().size());
        assertEquals(responsePoint.getId(), endpointPage.getData().get(0).getId());
        assertEquals(Endpoint.EndpointType.DEFAULT, responsePoint.getType());
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
                .contentType(ContentType.JSON)
                .get("/endpoints")
                .then()
                .statusCode(200)
                .extract().response();

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        Endpoint[] endpoints = endpointPage.getData().toArray(new Endpoint[0]);
        assertEquals(stats[0], endpoints.length);

        response = given()
                .header(identityHeader)
                .queryParam("sort_by", "enabled")
                .when()
                .contentType(ContentType.JSON)
                .get("/endpoints")
                .then()
                .statusCode(200)
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
                .contentType(ContentType.JSON)
                .get("/endpoints")
                .then()
                .statusCode(200)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        endpoints = endpointPage.getData().toArray(new Endpoint[0]);
        assertEquals(20, endpoints.length);
        assertEquals("Default endpoint", endpoints[endpoints.length - 1].getName());
        assertEquals("Endpoint 1", endpoints[endpoints.length - 2].getName());
        assertEquals("Endpoint 26", endpoints[0].getName());
    }

    @Test
    void testWebhookAttributes() {
        String tenant = "testWebhookAttributes";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        // Add new endpoints
        WebhookAttributes webAttr = new WebhookAttributes();
        webAttr.setMethod(WebhookAttributes.HttpType.POST);
        webAttr.setDisableSSLVerification(false);
        webAttr.setSecretToken("my-super-secret-token");
        webAttr.setBasicAuthentication(new WebhookAttributes.BasicAuthentication("myuser", "mypassword"));
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

        // Fetch single endpoint also and verify
        Endpoint responsePointSingle = fetchSingle(responsePoint.getId(), identityHeader);

        assertNotNull(responsePoint.getProperties());
        assertTrue(responsePointSingle.isEnabled());
        assertNotNull(responsePointSingle.getProperties());
        assertTrue(responsePointSingle.getProperties() instanceof WebhookAttributes);

        WebhookAttributes attr = (WebhookAttributes) responsePointSingle.getProperties();
        assertNotNull(attr.getBasicAuthentication());
        assertEquals("mypassword", attr.getBasicAuthentication().getPassword());
    }

    @Test
    void testAddEndpointEmailSubscription() {
        String tenant = "adding-email-subscription";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        // Add new EmailSubscriptionEndpoint
        EmailSubscriptionAttributes attributes = new EmailSubscriptionAttributes();

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.EMAIL_SUBSCRIPTION);
        ep.setName("Endpoint: EmailSubscription");
        ep.setDescription("Subscribe!");
        ep.setEnabled(true);
        ep.setProperties(attributes);

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

        // Delete
        String body =
            given()
                .header(identityHeader)
                .when().delete("/endpoints/" + responsePoint.getId())
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
                .contentType(ContentType.JSON)
                .delete("/endpoints/email/subscription/insights/" + ResourceHelpers.TEST_APP_NAME + "/instant")
                .then().statusCode(404);
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .delete("/endpoints/email/subscription/" + ResourceHelpers.TEST_BUNDLE_NAME  + "/policies/instant")
                .then().statusCode(404);
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .put("/endpoints/email/subscription/insights/" + ResourceHelpers.TEST_APP_NAME + "/instant")
                .then().statusCode(404);
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .put("/endpoints/email/subscription/" + ResourceHelpers.TEST_BUNDLE_NAME  + "/policies/instant")
                .then().statusCode(404);

        // Unknown bundle/apps give 404
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .delete("/endpoints/email/subscription/idontexist/meneither/instant")
                .then().statusCode(404);
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .put("/endpoints/email/subscription/idontexist/meneither/instant")
                .then().statusCode(404);

        // Disable everything as preparation
        // insights/policies instant and daily
        // TEST_BUNDLE_NAME/TEST_APP_NAME instant and daily
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .delete("/endpoints/email/subscription/insights/policies/instant")
                .then().statusCode(200);
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .delete("/endpoints/email/subscription/" + ResourceHelpers.TEST_BUNDLE_NAME  + "/" + ResourceHelpers.TEST_APP_NAME + "/instant")
                .then().statusCode(200);
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .delete("/endpoints/email/subscription/insights/policies/daily")
                .then().statusCode(200);
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .delete("/endpoints/email/subscription/" + ResourceHelpers.TEST_BUNDLE_NAME  + "/" + ResourceHelpers.TEST_APP_NAME + "/daily")
                .then().statusCode(200);

        assertNull(this.helpers.getSubscription(tenant, username, "insights", "policies", EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, "insights", "policies", EmailSubscriptionType.DAILY));
        assertNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.DAILY));

        // Enable instant on insights.policies
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .put("/endpoints/email/subscription/insights/policies/instant")
                .then().statusCode(200);

        assertNotNull(this.helpers.getSubscription(tenant, username, "insights", "policies", EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, "insights", "policies", EmailSubscriptionType.DAILY));
        assertNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.DAILY));

        // Enable daily on insights.policies
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .put("/endpoints/email/subscription/insights/policies/daily")
                .then().statusCode(200);

        assertNotNull(this.helpers.getSubscription(tenant, username, "insights", "policies", EmailSubscriptionType.INSTANT));
        assertNotNull(this.helpers.getSubscription(tenant, username, "insights", "policies", EmailSubscriptionType.DAILY));
        assertNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.DAILY));

        // Enable instant on TEST_BUNDLE_NAME.TEST_APP_NAME
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .put("/endpoints/email/subscription/" + ResourceHelpers.TEST_BUNDLE_NAME + "/" + ResourceHelpers.TEST_APP_NAME + "/instant")
                .then().statusCode(200);

        assertNotNull(this.helpers.getSubscription(tenant, username, "insights", "policies", EmailSubscriptionType.INSTANT));
        assertNotNull(this.helpers.getSubscription(tenant, username, "insights", "policies", EmailSubscriptionType.DAILY));
        assertNotNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.DAILY));

        // Disable daily on insights.policies
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .delete("/endpoints/email/subscription/insights/policies/daily")
                .then().statusCode(200);

        assertNotNull(this.helpers.getSubscription(tenant, username, "insights", "policies", EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, "insights", "policies", EmailSubscriptionType.DAILY));
        assertNotNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, ResourceHelpers.TEST_BUNDLE_NAME, ResourceHelpers.TEST_APP_NAME, EmailSubscriptionType.DAILY));

        // Disable instant on insights.policies
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .delete("/endpoints/email/subscription/insights/policies/instant")
                .then().statusCode(200);

        assertNull(this.helpers.getSubscription(tenant, username, "insights", "policies", EmailSubscriptionType.INSTANT));
        assertNull(this.helpers.getSubscription(tenant, username, "insights", "policies", EmailSubscriptionType.DAILY));
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
                .body(is("{\"data\":[],\"links\":{},\"meta\":{\"count\":0}}"));

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
