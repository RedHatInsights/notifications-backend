package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.EmailSubscriptionRepository;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointStatus;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.IntegrationTemplate;
import com.redhat.cloud.notifications.models.Template;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrlValidator;
import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrlValidatorTest;
import com.redhat.cloud.notifications.openbridge.Bridge;
import com.redhat.cloud.notifications.openbridge.BridgeHelper;
import com.redhat.cloud.notifications.openbridge.BridgeItemList;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import com.redhat.cloud.notifications.routers.models.RequestEmailSubscriptionProperties;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.db.ResourceHelpers.TEST_APP_NAME;
import static com.redhat.cloud.notifications.db.ResourceHelpers.TEST_BUNDLE_NAME;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;
import static com.redhat.cloud.notifications.routers.EndpointResource.OB_PROCESSOR_NAME;
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
public class EndpointResourceTest extends DbIsolatedTest {

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

    @Inject
    ResourceHelpers helpers;

    @Inject
    EmailSubscriptionRepository emailSubscriptionRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    BridgeHelper bridgeHelper;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    FeatureFlipper featureFlipper;

    @Test
    void testEndpointAdding() {
        String accountId = "empty";
        String orgId = "empty";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

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
        properties.setUrl(getMockServerUrl());

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("endpoint to find");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);
        ep.setServerErrors(3);

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
        assertEquals(3, responsePoint.getInteger("server_errors"));
        assertEquals(EndpointStatus.READY.toString(), responsePoint.getString("status"));

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
        assertEquals(EndpointStatus.READY.toString(), responsePoint.getString("status"));

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
        assertEquals(0, responsePointSingle.getInteger("server_errors"));

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
                .contentType(TEXT);

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

    @Test
    void testRepeatedEndpointName() {
        try {
            featureFlipper.setEnforceIntegrationNameUnicity(true);
            String orgId = "repeatEndpoint";
            String userName = "user";

            String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(orgId, orgId, userName);
            Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

            MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

            // Endpoint1
            WebhookProperties properties = new WebhookProperties();
            properties.setMethod(HttpType.POST);
            properties.setDisableSslVerification(false);
            properties.setSecretToken("my-super-secret-token");
            properties.setUrl(getMockServerUrl());

            Endpoint endpoint1 = new Endpoint();
            endpoint1.setType(EndpointType.WEBHOOK);
            endpoint1.setName("Endpoint1");
            endpoint1.setDescription("needle in the haystack");
            endpoint1.setEnabled(true);
            endpoint1.setProperties(properties);
            endpoint1.setServerErrors(3);

            Response response = given()
                    .header(identityHeader)
                    .when()
                    .contentType(JSON)
                    .body(Json.encode(endpoint1))
                    .post("/endpoints")
                    .then()
                    .statusCode(200)
                    .extract().response();

            String endpoint1Id = new JsonObject(response.getBody().asString()).getString("id");
            assertNotNull(endpoint1Id);

            // Trying to add the same endpoint name again results in a 400 error
            given()
                    .header(identityHeader)
                    .when()
                    .contentType(JSON)
                    .body(Json.encode(endpoint1))
                    .post("/endpoints")
                    .then()
                    .statusCode(400);

            // Endpoint2
            Endpoint ep = new Endpoint();
            ep.setType(EndpointType.WEBHOOK);
            ep.setName("Endpoint2");
            ep.setDescription("needle in the haystack");
            ep.setEnabled(true);
            ep.setProperties(properties);
            ep.setServerErrors(3);

            given()
                    .header(identityHeader)
                    .when()
                    .contentType(JSON)
                    .body(Json.encode(ep))
                    .post("/endpoints")
                    .then()
                    .statusCode(200);

            // Different endpoint type with same name
            CamelProperties camelProperties = new CamelProperties();
            camelProperties.setBasicAuthentication(new BasicAuthentication());
            camelProperties.setExtras(Map.of());
            camelProperties.setSecretToken("secret");
            camelProperties.setUrl("http://nowhere");

            ep = new Endpoint();
            ep.setType(EndpointType.CAMEL);
            ep.setSubType("stuff");
            ep.setName("Endpoint1");
            ep.setDescription("needle in the haystack");
            ep.setEnabled(true);
            ep.setProperties(camelProperties);
            ep.setServerErrors(3);

            given()
                    .header(identityHeader)
                    .when()
                    .contentType(JSON)
                    .body(Json.encode(ep))
                    .post("/endpoints")
                    .then()
                    .statusCode(400);

            // Updating endpoint1 name is possible
            endpoint1.setName("Endpoint1-updated");
            given()
                    .header(identityHeader)
                    .contentType(JSON)
                    .body(Json.encode(endpoint1))
                    .when()
                    .put("/endpoints/" + endpoint1Id)
                    .then()
                    .statusCode(200);

            // Updating to the name of an already existing endpoint is not possible
            endpoint1.setName("Endpoint2");
            given()
                    .header(identityHeader)
                    .contentType(JSON)
                    .body(Json.encode(endpoint1))
                    .when()
                    .put("/endpoints/" + endpoint1Id)
                    .then()
                    .statusCode(400);
        } finally {
            featureFlipper.setEnforceIntegrationNameUnicity(false);
        }
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
        String accountId = "validation";
        String orgId = "validation2";
        String userName = "testEndpointValidation";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        // Add new endpoint without properties
        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("endpoint with missing properties");
        ep.setDescription("Destined to fail");
        ep.setEnabled(true);

        expectReturn400(identityHeader, ep);

        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(HttpType.POST);
        properties.setDisableSslVerification(false);
        properties.setSecretToken("my-super-secret-token");
        properties.setUrl(getMockServerUrl());

        // Test with properties, but without endpoint type
        ep.setProperties(properties);
        ep.setType(null);

        expectReturn400(identityHeader, ep);

        // Test with incorrect webhook properties
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("endpoint with incorrect webhook properties");
        properties.setMethod(null);
        expectReturn400(identityHeader, ep);

        // Type and attributes don't match
        properties.setMethod(HttpType.POST);
        ep.setType(EndpointType.EMAIL_SUBSCRIPTION);
        expectReturn400(identityHeader, ep);

        ep.setName("endpoint with subtype too long");
        ep.setType(EndpointType.CAMEL);
        ep.setSubType("something-longer-than-20-chars");
        expectReturn400(identityHeader, ep);
    }

    private void expectReturn400(Header identityHeader, Endpoint ep) {
        given()
                 .header(identityHeader)
                 .when()
                 .contentType(JSON)
                 .body(Json.encode(ep))
                 .post("/endpoints")
                 .then()
                 .statusCode(400)
                 .contentType(JSON);
    }

    @Test
    void addCamelEndpoint() {

        String accountId = "empty";
        String orgId = "empty";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        CamelProperties cAttr = new CamelProperties();
        cAttr.setDisableSslVerification(false);
        cAttr.setUrl(getMockServerUrl());
        cAttr.setBasicAuthentication(new BasicAuthentication("testuser", "secret"));
        Map<String, String> extras = new HashMap<>();
        extras.put("template", "11");
        cAttr.setExtras(extras);

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.CAMEL);
        ep.setSubType("ansible");
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
            assertEquals("ansible", endpoint.getString("sub_type"));
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
    void addBogusCamelEndpoint() {

        String accountId = "empty";
        String orgId = "empty";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        CamelProperties cAttr = new CamelProperties();
        cAttr.setDisableSslVerification(false);
        cAttr.setUrl(getMockServerUrl());
        cAttr.setBasicAuthentication(new BasicAuthentication("testuser", "secret"));
        Map<String, String> extras = new HashMap<>();
        extras.put("template", "11");
        cAttr.setExtras(extras);

        // This is bogus because it has no sub_type
        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.CAMEL);
        ep.setName("Push the camel through the needle's ear");
        ep.setDescription("How many humps has a camel?");
        ep.setEnabled(true);
        ep.setProperties(cAttr);

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(400);

        featureFlipper.setObEnabled(true);

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(400);

        featureFlipper.setObEnabled(false);

    }

    @Test
    void addOpenBridgeEndpoint() {

        String accountId = "empty";
        String orgId = "empty";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        setupTransformationTemplate();

        CamelProperties cAttr = new CamelProperties();
        cAttr.setDisableSslVerification(false);
        cAttr.setUrl(getMockServerUrl());
        Map<String, String> extras = new HashMap<>();
        extras.put("channel", "#notifications");
        cAttr.setExtras(extras);

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.CAMEL);
        ep.setSubType("slack");
        ep.setName("Push the camel through the needle's ear");
        ep.setDescription("I guess the camel is slacking");
        ep.setEnabled(true);
        ep.setProperties(cAttr);
        ep.setStatus(EndpointStatus.DELETING); // Trying to set other status

        featureFlipper.setObEnabled(true);

        // First we try with bogus values for the OB endpoint itself (no valid bridge)
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(500);

        // Now set up some mock OB endpoints (simulate valid bridge)
        Bridge bridge = new Bridge("321", "http://some.events/", "my bridge");
        BridgeItemList<Bridge> bridgeList = new BridgeItemList<>();
        bridgeList.setSize(1);
        bridgeList.setTotal(1);
        List<Bridge> items = new ArrayList<>();
        items.add(bridge);
        bridgeList.setItems(items);
        Map<String, String> auth = new HashMap<>();
        auth.put("access_token", "li-la-lu-token");
        Map<String, String> processor = new HashMap<>();
        processor.put("id", "p-my-id");

        MockServerConfig.addOpenBridgeEndpoints(auth, bridgeList, processor);
        bridgeHelper.setOurBridgeName("my bridge");

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
        assertEquals(EndpointStatus.PROVISIONING.toString(), responsePoint.getString("status"));

        try {
            JsonObject endpoint = fetchSingle(id, identityHeader);
            JsonObject properties = responsePoint.getJsonObject("properties");
            assertNotNull(properties);
            assertTrue(endpoint.getBoolean("enabled"));
            assertEquals("slack", endpoint.getString("sub_type"));
            JsonObject extrasObject = properties.getJsonObject("extras");
            assertNotNull(extrasObject);
            String channel  = extrasObject.getString("channel");
            assertEquals("#notifications", channel);
            assertEquals(responsePoint.getString("id"), extrasObject.getString(OB_PROCESSOR_NAME));

            ep.getProperties(CamelProperties.class).getExtras().put("channel", "#updated");
            // Now update
            responseBody = given()
                    .header(identityHeader)
                    .contentType(JSON)
                    .body(Json.encode(ep))
                    .when()
                    .put("/endpoints/" + id)
                    .then()
                    .statusCode(200)
                    .extract().asString();

            assertNotNull(responseBody);

        } finally {

            given()
                    .header(identityHeader)
                    .when().delete("/endpoints/" + id)
                    .then()
                    .statusCode(204);
        }

        MockServerConfig.clearOpenBridgeEndpoints(bridge);
        featureFlipper.setObEnabled(false);
    }

    @Transactional
    void setupTransformationTemplate() {
        // Set up the transformationTemplate in the DB
        Template template = new Template();
        template.setName("aTemplate");
        template.setDescription("what's this?");
        template.setData("Li la lu");
        entityManager.persist(template);

        IntegrationTemplate gt = new IntegrationTemplate();
        gt.setTemplateKind(IntegrationTemplate.TemplateKind.DEFAULT);
        gt.setIntegrationType("slack");
        gt.setTheTemplate(template);
        entityManager.persist(gt);
    }

    @Test
    void testEndpointUpdates() {
        String accountId = "updates";
        String orgId = "updates2";
        String userName = "testEndpointUpdates";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

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
        properties.setUrl(getMockServerUrl());

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("endpoint to find");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);
        ep.setServerErrors(7);

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
        assertEquals(7, responsePoint.getInteger("server_errors"));

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
        assertEquals(0, updatedEndpoint.getInteger("server_errors"));
    }

    private static Stream<Arguments> testEndpointTypeQuery() {
        return Stream.of(
                Arguments.of(Set.of(EndpointType.WEBHOOK)),
                Arguments.of(Set.of(EndpointType.CAMEL)),
                        Arguments.of(Set.of(EndpointType.WEBHOOK, EndpointType.CAMEL))
        );
    }

    @ParameterizedTest
    @MethodSource
    void testEndpointTypeQuery(Set<EndpointType> types) {
        String accountId = "limiter";
        String orgId = "limiter2";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        // Add webhook
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(HttpType.POST);
        properties.setDisableSslVerification(false);
        properties.setSecretToken("my-super-secret-token");
        properties.setUrl(getMockServerUrl());

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

        // Add Camel
        CamelProperties camelProperties = new CamelProperties();
        camelProperties.setDisableSslVerification(false);
        camelProperties.setSecretToken("my-super-secret-token");
        camelProperties.setUrl(getMockServerUrl());
        camelProperties.setExtras(new HashMap<>());

        Endpoint camelEp = new Endpoint();
        camelEp.setType(EndpointType.CAMEL);
        camelEp.setSubType("demo");
        camelEp.setName("endpoint 2 to find");
        camelEp.setDescription("needle in the haystack");
        camelEp.setEnabled(true);
        camelEp.setProperties(camelProperties);

        response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(camelEp))
                .post("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(Endpoint.class);
        assertNotNull(responsePoint.getString("id"));

        // Fetch the list to ensure everything was inserted correctly.
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
        assertEquals(2, endpoints.size());

        // Fetch the list with types
        response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .queryParam("type", types)
                .when().get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        endpoints = endpointPage.getData();

        // Ensure there is only the requested types
        assertEquals(
                types,
                endpoints.stream().map(Endpoint::getType).collect(Collectors.toSet())
        );
    }

    @Test
    void testEndpointLimiter() {
        String accountId = "limiter";
        String orgId = "limiter2";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);
        addEndpoints(29, identityHeader);

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
        String accountId = "testSortingOrder";
        String orgId = "testSortingOrder2";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        // Create 50 test-ones with sanely sortable name & enabled & disabled & type
        int[] stats = helpers.createTestEndpoints(accountId, orgId, 50);
        int disableCount = stats[1];
        int webhookCount = stats[2];

        Response response = given()
                .header(identityHeader)
                .when()
                .get("/endpoints?limit=100")
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
                .get("/endpoints?limit=100")
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
                .get("/endpoints?limit=100")
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

        given()
                .header(identityHeader)
                .queryParam("sort_by", "hulla:desc")
                .when()
                .get("/endpoints?limit=100")
                .then()
                // NOTIF-674 Should have status code 400
                .statusCode(500);

    }

    @Test
    void testWebhookAttributes() {
        String accountId = "testWebhookAttributes";
        String orgId = "testWebhookAttributes2";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        // Add new endpoints
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(HttpType.POST);
        properties.setDisableSslVerification(false);
        properties.setSecretToken("my-super-secret-token");
        properties.setBasicAuthentication(new BasicAuthentication("myuser", "mypassword"));
        properties.setUrl(getMockServerUrl());

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
        String accountId = "adding-email-subscription";
        String orgId = "adding-email-subscription2";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

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
        webhookProperties.setUrl(getMockServerUrl());
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
    void testAddEndpointEmailSubscriptionRbac() {
        String accountId = "adding-email-subscription";
        String orgId = "adding-email-subscription2";
        String userName = "user";
        String validGroupId = "f85517d0-063b-4eed-a501-e79ffc1f5ad3";
        String unknownGroupId = "f44f50d5-acab-482c-a3cf-087faf2c709c";

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);
        MockServerConfig.addGroupResponse(identityHeaderValue, validGroupId, 200);
        MockServerConfig.addGroupResponse(identityHeaderValue, unknownGroupId, 404);

        // valid group id
        RequestEmailSubscriptionProperties requestProps = new RequestEmailSubscriptionProperties();
        requestProps.setGroupId(UUID.fromString(validGroupId));

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
        String endpointId = responsePoint.getString("id");
        assertNotNull(endpointId);

        // Same group again yields the same endpoint id
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
        assertEquals(endpointId, responsePoint.getString("id"));

        // Invalid group is a bad request (i.e. group does not exist)
        requestProps.setGroupId(UUID.fromString(unknownGroupId));
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(requestProps))
                .post("/endpoints/system/email_subscription")
                .then()
                .statusCode(400)
                .contentType(JSON)
                .extract().response();

        // Can't specify admin and group - bad request
        requestProps.setGroupId(UUID.fromString(validGroupId));
        requestProps.setOnlyAdmins(true);
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(requestProps))
                .post("/endpoints/system/email_subscription")
                .then()
                .statusCode(400)
                .contentType(JSON)
                .extract().response();
    }

    @Test
    void testEmailSubscription() {
        String accountId = "test-subscription";
        String orgId = "test-subscription-org-id";
        String username = "test-user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, username);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        helpers.createTestAppAndEventTypes();
        // invalid bundle/application combination gives a 404
        given()
                .header(identityHeader)
                .when()
                .delete("/endpoints/email/subscription/rhel/" + TEST_APP_NAME + "/instant")
                .then().statusCode(404)
                .contentType(TEXT);
        given()
                .header(identityHeader)
                .when()
                .delete("/endpoints/email/subscription/" + TEST_BUNDLE_NAME + "/policies/instant")
                .then().statusCode(404)
                .contentType(TEXT);
        given()
                .header(identityHeader)
                .when()
                .put("/endpoints/email/subscription/rhel/" + TEST_APP_NAME + "/instant")
                .then().statusCode(404)
                .contentType(TEXT);
        given()
                .header(identityHeader)
                .when()
                .put("/endpoints/email/subscription/" + TEST_BUNDLE_NAME + "/policies/instant")
                .then().statusCode(404)
                .contentType(TEXT);

        // Unknown bundle/apps give 404
        given()
                .header(identityHeader)
                .when()
                .delete("/endpoints/email/subscription/idontexist/meneither/instant")
                .then().statusCode(404)
                .contentType(TEXT);
        given()
                .header(identityHeader)
                .when()
                .put("/endpoints/email/subscription/idontexist/meneither/instant")
                .then().statusCode(404)
                .contentType(TEXT);

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

        assertNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, "rhel", "policies", INSTANT));
        assertNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, "rhel", "policies", DAILY));
        assertNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, TEST_BUNDLE_NAME, TEST_APP_NAME, INSTANT));
        assertNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, TEST_BUNDLE_NAME, TEST_APP_NAME, DAILY));

        // Enable instant on rhel.policies
        given()
                .header(identityHeader)
                .when()
                .put("/endpoints/email/subscription/rhel/policies/instant")
                .then().statusCode(200).contentType(JSON);

        assertNotNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, "rhel", "policies", INSTANT));
        assertNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, "rhel", "policies", DAILY));
        assertNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, TEST_BUNDLE_NAME, TEST_APP_NAME, INSTANT));
        assertNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, TEST_BUNDLE_NAME, TEST_APP_NAME, DAILY));

        // Enable daily on rhel.policies
        given()
                .header(identityHeader)
                .when()
                .put("/endpoints/email/subscription/rhel/policies/daily")
                .then().statusCode(200).contentType(JSON);

        assertNotNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, "rhel", "policies", INSTANT));
        assertNotNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, "rhel", "policies", DAILY));
        assertNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, TEST_BUNDLE_NAME, TEST_APP_NAME, INSTANT));
        assertNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, TEST_BUNDLE_NAME, TEST_APP_NAME, DAILY));

        // Enable instant on TEST_BUNDLE_NAME.TEST_APP_NAME
        given()
                .header(identityHeader)
                .when()
                .put("/endpoints/email/subscription/" + TEST_BUNDLE_NAME + "/" + TEST_APP_NAME + "/instant")
                .then().statusCode(200).contentType(JSON);

        assertNotNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, "rhel", "policies", INSTANT));
        assertNotNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, "rhel", "policies", DAILY));
        assertNotNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, TEST_BUNDLE_NAME, TEST_APP_NAME, INSTANT));
        assertNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, TEST_BUNDLE_NAME, TEST_APP_NAME, DAILY));

        // Disable daily on rhel.policies
        given()
                .header(identityHeader)
                .when()
                .delete("/endpoints/email/subscription/rhel/policies/daily")
                .then().statusCode(200).contentType(JSON);

        assertNotNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, "rhel", "policies", INSTANT));
        assertNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, "rhel", "policies", DAILY));
        assertNotNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, TEST_BUNDLE_NAME, TEST_APP_NAME, INSTANT));
        assertNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, TEST_BUNDLE_NAME, TEST_APP_NAME, DAILY));

        // Disable instant on rhel.policies
        given()
                .header(identityHeader)
                .when()
                .delete("/endpoints/email/subscription/rhel/policies/instant")
                .then().statusCode(200).contentType(JSON);

        assertNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, "rhel", "policies", INSTANT));
        assertNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, "rhel", "policies", DAILY));
        assertNotNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, TEST_BUNDLE_NAME, TEST_APP_NAME, INSTANT));
        assertNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, TEST_BUNDLE_NAME, TEST_APP_NAME, DAILY));
    }

    @Test
    void testUnknownEndpointTypes() {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("test-tenant", "test-orgid", "test-user");
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        given()
                .header(identityHeader)
                .queryParam("type", "foo")
                .when().get("/endpoints")
                .then()
                .statusCode(400)
                .body(is("Unknown endpoint type: [foo]"));

        given()
                .header(identityHeader)
                .queryParam("type", EndpointType.WEBHOOK.toString())
                .queryParam("type", "bar")
                .when().get("/endpoints")
                .then()
                .statusCode(400)
                .body(is("Unknown endpoint type: [bar]"));
    }

    @Test
    void testConnectionCount() {
        String accountId = "count";
        String orgId = "count2";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

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
            properties.setUrl(getMockServerUrl());

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

    @Test
    void testActive() {
        String orgId = "queries-without-type";
        String username = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, orgId, username);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        // stats[0] is the count
        // stats[1] are the inactive ones
        int[] stats = resourceHelpers.createTestEndpoints(TestConstants.DEFAULT_ACCOUNT_ID, orgId, 11);

        // Get all endpoints
        Response response = given()
                .header(identityHeader)
                .when()
                .get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        assertEquals(stats[0], endpointPage.getMeta().getCount());
        assertEquals(stats[0], endpointPage.getData().size());

        // Only active
        response = given()
                .header(identityHeader)
                .when()
                .queryParam("active", "true")
                .get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        assertEquals(stats[0] - stats[1], endpointPage.getMeta().getCount());
        assertEquals(stats[0] - stats[1], endpointPage.getData().size());

        // Only inactive
        response = given()
                .header(identityHeader)
                .when()
                .queryParam("active", "false")
                .get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        assertEquals(stats[1], endpointPage.getMeta().getCount());
        assertEquals(stats[1], endpointPage.getData().size());

    }

    @Test
    void testSearch() {
        String accountId = "search";
        String orgId = "search2";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        addEndpoints(10, identityHeader);
        Response response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .queryParam("limit", "20")
                .queryParam("offset", "0")
                .queryParam("name", "2")
                .when().get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        assertEquals(1, endpointPage.getMeta().getCount());
        assertEquals(1, endpointPage.getData().size());
        assertEquals("Endpoint 2", endpointPage.getData().get(0).getName());

        response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .queryParam("limit", "20")
                .queryParam("offset", "0")
                .queryParam("name", "foo")
                .when().get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        assertEquals(0, endpointPage.getMeta().getCount());
        assertEquals(0, endpointPage.getData().size());
    }

    @Test
    void testSearchWithType() {
        String accountId = "search-type";
        String orgId = "search-type2";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        addEndpoints(10, identityHeader);
        Response response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .queryParam("limit", "20")
                .queryParam("offset", "0")
                .queryParam("name", "2")
                .queryParam("type", "WEBHOOK")
                .when().get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        assertEquals(1, endpointPage.getMeta().getCount());
        assertEquals(1, endpointPage.getData().size());
        assertEquals("Endpoint 2", endpointPage.getData().get(0).getName());

        response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .queryParam("limit", "20")
                .queryParam("offset", "0")
                .queryParam("name", "foo")
                .queryParam("type", "WEBHOOK")
                .when().get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        assertEquals(0, endpointPage.getMeta().getCount());
        assertEquals(0, endpointPage.getData().size());
    }

    /**
     * Tests that when an invalid URL is provided via the endpoint's properties, regardless if those properties are
     * {@link CamelProperties} or {@link WebhookProperties}, the proper constraint violation message is returned from
     * the handler.
     */
    @Test
    void testEndpointInvalidUrls() {
        // Set up the RBAC access for the test.
        final String orgId = "endpoint-invalid-urls";
        final String userName = "user";

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(orgId, orgId, userName);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        // Set up the fixture data.
        final var disableSslVerification = false;
        final HttpType method = HttpType.POST;
        final String password = "endpoint-invalid-urls-basic-authentication-password";
        final String username = "endpoint-invalid-urls-basic-authentication-username";
        final String secretToken = "endpoint-invalid-urls-secret-token";

        // Create the properties for the endpoint. Leave the URL so that we can set it afterwards.
        final var camelProperties = new CamelProperties();
        camelProperties.setBasicAuthentication(new BasicAuthentication(username, password));
        camelProperties.setDisableSslVerification(disableSslVerification);
        camelProperties.setSecretToken(secretToken);

        final var webhookProperties = new WebhookProperties();
        webhookProperties.setBasicAuthentication(new BasicAuthentication(username, password));
        webhookProperties.setDisableSslVerification(disableSslVerification);
        webhookProperties.setMethod(method);
        webhookProperties.setSecretToken(secretToken);

        // Create an endpoint without the type and the properties set.
        final var name = "endpoint-invalid-urls-name";
        final var description = "endpoint-invalid-urls-description";
        final var enabled = true;
        final var serverErrors = 0;
        final var subType = "slack";

        final var endpoint = new Endpoint();
        endpoint.setDescription(description);
        endpoint.setEnabled(enabled);
        endpoint.setName(name);
        endpoint.setServerErrors(serverErrors);
        endpoint.setSubType(subType);

        // Create a simple class to make testing easier.
        final class TestCase {
            public final String expectedErrorMessage;
            public final String[] testUrls;

            TestCase(final String expectedErrorMessage, final String[] testUrls) {
                this.expectedErrorMessage = expectedErrorMessage;
                this.testUrls = testUrls;
            }
        }

        // Create all the test cases we will be testing in this test.
        final List<TestCase> testCases = new ArrayList<>();
        testCases.add(
            new TestCase(ValidNonPrivateUrlValidator.INVALID_URL, ValidNonPrivateUrlValidatorTest.malformedUrls)
        );

        testCases.add(
            new TestCase(ValidNonPrivateUrlValidator.INVALID_URL, ValidNonPrivateUrlValidatorTest.malformedUris)
        );

        testCases.add(
            new TestCase(ValidNonPrivateUrlValidator.INVALID_SCHEME, ValidNonPrivateUrlValidatorTest.invalidSchemes)
        );

        testCases.add(
            new TestCase(ValidNonPrivateUrlValidator.PRIVATE_IP, ValidNonPrivateUrlValidatorTest.internalHosts)
        );

        testCases.add(
            new TestCase(
                ValidNonPrivateUrlValidator.UNKNOWN_HOST,
                ValidNonPrivateUrlValidatorTest.unknownHosts
            )
        );

        // Test the URLs with both camel and webhook endpoints.
        for (final var testCase : testCases) {
            for (final var url : testCase.testUrls) {
                // Test with a camel endpoint.
                camelProperties.setUrl(url);
                endpoint.setType(EndpointType.CAMEL);
                endpoint.setProperties(camelProperties);

                final String camelResponse =
                    given()
                        .header(identityHeader)
                        .when()
                        .contentType(JSON)
                        .body(Json.encode(endpoint))
                        .post("/endpoints")
                        .then()
                        .statusCode(400)
                        .extract()
                        .asString();

                final String camelConstraintViolation = TestHelpers.extractConstraintViolationFromResponse(camelResponse);

                Assertions.assertEquals(testCase.expectedErrorMessage, camelConstraintViolation, String.format("unexpected constraint violation for url \"%s\"", url));

                // Test with a webhook endpoint.
                webhookProperties.setUrl(url);
                endpoint.setType(EndpointType.WEBHOOK);
                endpoint.setProperties(webhookProperties);

                final String webhookResponse =
                    given()
                        .header(identityHeader)
                        .when()
                        .contentType(JSON)
                        .body(Json.encode(endpoint))
                        .post("/endpoints")
                        .then()
                        .statusCode(400)
                        .extract()
                        .asString();

                final String webhookConstraintViolation = TestHelpers.extractConstraintViolationFromResponse(webhookResponse);

                Assertions.assertEquals(testCase.expectedErrorMessage, webhookConstraintViolation, String.format("unexpected constraint violation for url \"%s\"", url));
            }
        }
    }

    /**
     * Tests that when a valid URL is provided via the endpoint's properties, regardless if those properties are
     * {@link CamelProperties} or {@link WebhookProperties}, no constraint violations are raised.
     */
    @Test
    void testEndpointValidUrls() {
        // Set up the RBAC access for the test.
        final String orgId = "endpoint-invalid-urls";
        final String userName = "user";

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(orgId, orgId, userName);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        // Set up the fixture data.
        final var disableSslVerification = false;
        final HttpType method = HttpType.POST;
        final String password = "endpoint-invalid-urls-basic-authentication-password";
        final String username = "endpoint-invalid-urls-basic-authentication-username";
        final String secretToken = "endpoint-invalid-urls-secret-token";

        // Create the properties for the endpoint. Leave the URL so that we can set it afterwards.
        final var camelProperties = new CamelProperties();
        camelProperties.setBasicAuthentication(new BasicAuthentication(username, password));
        camelProperties.setDisableSslVerification(disableSslVerification);
        camelProperties.setSecretToken(secretToken);

        final var webhookProperties = new WebhookProperties();
        webhookProperties.setBasicAuthentication(new BasicAuthentication(username, password));
        webhookProperties.setDisableSslVerification(disableSslVerification);
        webhookProperties.setMethod(method);
        webhookProperties.setSecretToken(secretToken);

        // Create an endpoint without the type and the properties set.
        final var name = "endpoint-invalid-urls-name";
        final var description = "endpoint-invalid-urls-description";
        final var enabled = true;
        final var serverErrors = 0;
        final var subType = "slack";

        final var endpoint = new Endpoint();
        endpoint.setDescription(description);
        endpoint.setEnabled(enabled);
        endpoint.setName(name);
        endpoint.setServerErrors(serverErrors);
        endpoint.setSubType(subType);

        for (final var url : ValidNonPrivateUrlValidatorTest.validUrls) {
            // Test with a camel endpoint.
            camelProperties.setUrl(url);
            endpoint.setType(EndpointType.CAMEL);
            endpoint.setProperties(camelProperties);

            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(endpoint))
                .post("/endpoints")
                .then()
                .statusCode(200);

            // Test with a webhook endpoint.
            webhookProperties.setUrl(url);
            endpoint.setType(EndpointType.WEBHOOK);
            endpoint.setProperties(webhookProperties);

            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(endpoint))
                .post("/endpoints")
                .then()
                .statusCode(200);
        }
    }

    private void assertSystemEndpointTypeError(String message, EndpointType endpointType) {
        assertTrue(message.contains(String.format(
                "Is not possible to create or alter endpoint with type %s, check API for alternatives",
                endpointType
        )));
    }

    private void addEndpoints(int count, Header identityHeader) {
        for (int i = 0; i < count; i++) {
            // Add new endpoints
            WebhookProperties properties = new WebhookProperties();
            properties.setMethod(HttpType.POST);
            properties.setDisableSslVerification(false);
            properties.setSecretToken("my-super-secret-token");
            properties.setUrl(getMockServerUrl() + "/" + i);

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
    }
}
