package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.CounterAssertionHelper;
import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockserver.model.HttpRequest;

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.redhat.cloud.notifications.TestHelpers.serializeAction;
import static com.redhat.cloud.notifications.events.EventConsumer.REJECTED_COUNTER_NAME;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
@TestMethodOrder(MethodOrderer.Alphanumeric.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LifecycleITest {

    /*
     * In the tests below, most JSON responses are verified using JsonObject/JsonArray instead of deserializing these
     * responses into model instances and checking their attributes values. That's because the model classes contain
     * attributes annotated with @JsonProperty(access = READ_ONLY) which can't be deserialized and therefore verified
     * here. The deserialization is still performed only to verify that the JSON responses data structure is correct.
     */

    private static final String APP_NAME = "policies-lifecycle-test";
    private static final String BUNDLE_NAME = "my-bundle";
    private static final String EVENT_TYPE_NAME = "all";

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_1_0;
    }

    private Header identityHeader;

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @Inject
    CounterAssertionHelper counterAssertionHelper;

    JsonObject theBundle;

    @BeforeAll
    void setup() {
        // Create Rbacs
        String tenant = "tenant";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

    }

    @Test
    void t00_setupBundle() {
        Bundle bundle = new Bundle(BUNDLE_NAME, "A bundle");
        Response response = given()
                .body(bundle)
                .contentType(ContentType.JSON)
                .basePath("/")
                .when()
                .post("/internal/bundles")
                .then()
                .statusCode(200)
                .extract().response();
        theBundle = new JsonObject(response.getBody().asString());
        theBundle.mapTo(Bundle.class);
    }

    @Test
    void t01_testAdding() {
        Application app = new Application();
        app.setName(APP_NAME);
        app.setDisplay_name("The best app in the life");
        app.setBundleId(UUID.fromString(theBundle.getString("id")));

        Response response = given()
                .when()
                .contentType(ContentType.JSON)
                .basePath("/")
                .body(Json.encode(app))
                .post("/internal/applications")
                .then()
                .statusCode(200)
                .extract().response();

        JsonObject appResponse = new JsonObject(response.getBody().asString());
        appResponse.mapTo(Application.class);
        assertNotNull(appResponse.getString("id"));
        assertEquals(theBundle.getString("id"), appResponse.getString("bundle_id"));

        // Create eventType
        EventType eventType = new EventType();
        eventType.setName(EVENT_TYPE_NAME);
        eventType.setDisplay_name("Policies will take care of the rules");
        eventType.setDescription("Policies is super cool, you should use it");

        response = given()
                .when()
                .contentType(ContentType.JSON)
                .basePath("/")
                .body(Json.encode(eventType))
                .post(String.format("/internal/applications/%s/eventTypes", appResponse.getString("id")))
                .then()
                .statusCode(200)
                .extract().response();

        JsonObject typeResponse = new JsonObject(response.getBody().asString());
        typeResponse.mapTo(EventType.class);
        assertNotNull(typeResponse.getString("id"));
        assertEquals(eventType.getDescription(), typeResponse.getString("description"));

        // Add new endpoints
        WebhookAttributes webAttr = new WebhookAttributes();
        webAttr.setMethod(HttpType.POST);
        webAttr.setDisableSSLVerification(true);
        webAttr.setSecretToken("super-secret-token");
        webAttr.setUrl(String.format("http://%s/test/lifecycle", mockServerConfig.getRunningAddress()));

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("positive feeling");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(webAttr);

        response = given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(200)
                .extract().response();

        JsonObject endpoint = new JsonObject(response.getBody().asString());
        endpoint.mapTo(Endpoint.class);
        assertNotNull(endpoint.getString("id"));

        webAttr = new WebhookAttributes();
        webAttr.setMethod(HttpType.POST);
        webAttr.setDisableSSLVerification(true);
        webAttr.setUrl(String.format("http://%s/test/lifecycle", mockServerConfig.getRunningAddress()));

        ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("negative feeling");
        ep.setDescription("I feel like dying");
        ep.setEnabled(true);
        ep.setProperties(webAttr);

        response = given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(200)
                .extract().response();

        JsonObject endpointFail = new JsonObject(response.getBody().asString());
        endpointFail.mapTo(Endpoint.class);
        assertNotNull(endpointFail.getString("id"));

        // Link an eventType to endpoints

        for (JsonObject endpointLink : List.of(endpoint, endpointFail)) {
            given()
                    .basePath(TestConstants.API_NOTIFICATIONS_V_1_0)
                    .header(identityHeader)
                    .when()
                    .contentType(ContentType.JSON)
                    .put(String.format("/notifications/eventTypes/%s/%s", typeResponse.getString("id"), endpointLink.getString("id")))
                    .then()
                    .statusCode(200);
        }
    }

    @Test
    void t02_pushMessage() throws Exception {
        counterAssertionHelper.saveCounterValuesBeforeTest(REJECTED_COUNTER_NAME);

        // These are succesful requests
        HttpRequest postReq = new HttpRequest()
                .withPath("/test/lifecycle")
                .withMethod("POST");
        CountDownLatch latch = new CountDownLatch(2);
        mockServerConfig.getMockServerClient()
                .withSecure(false)
                .when(postReq)
                .respond(req -> {
                    // Verify req parameters here, like the secret-token?
                    latch.countDown();
                    List<String> header = req.getHeader("X-Insight-Token");
                    if (header != null && header.size() == 1 && header.get(0).equals("super-secret-token")) {
                        return response()
                                .withStatusCode(200)
                                .withBody("Success");
                    }
                    return response()
                            .withStatusCode(400)
                            .withBody("{ \"message\": \"Time is running out\" }");
                });

        // Read the input file and send it
        Action targetAction = new Action();
        targetAction.setApplication(APP_NAME);
        targetAction.setBundle(BUNDLE_NAME);
        targetAction.setTimestamp(LocalDateTime.now());
        targetAction.setEventType(EVENT_TYPE_NAME);

        Map params = new HashMap();
        params.put("triggers", new HashMap());
        targetAction.setPayload(params);

        targetAction.setAccountId("tenant");

        String payload = serializeAction(targetAction);
        inMemoryConnector.source("ingress").send(payload);

//        InputStream is = getClass().getClassLoader().getResourceAsStream("input/platform.notifications.ingress.json");
//        String inputJson = IOUtils.toString(is, StandardCharsets.UTF_8);
//        ingressChan.send(inputJson);

        if (!latch.await(5, TimeUnit.SECONDS)) {
            fail("HttpServer never received the requests");
//            mockServerConfig.getMockServerClient().verify(postReq, VerificationTimes.exactly(2));
            HttpRequest[] httpRequests = mockServerConfig.getMockServerClient().retrieveRecordedRequests(postReq);
            assertEquals(2, httpRequests.length);
            // Verify calls were correct, sort first?
        }

        mockServerConfig.getMockServerClient().clear(postReq);
        counterAssertionHelper.assertIncrement(REJECTED_COUNTER_NAME, 0);
        counterAssertionHelper.clear();
    }

    @Test
    void t03_fetchHistory() {
        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/endpoints")
                .then()
                .statusCode(200)
                .extract().response();

        JsonArray endpoints = new JsonObject(response.getBody().asString()).getJsonArray("data");
        assertEquals(2, endpoints.size());

        for (int i = 0; i < endpoints.size(); i++) {
            JsonObject ep = endpoints.getJsonObject(i);
            ep.mapTo(Endpoint.class);
            // Fetch the notification history for the endpoints
            response = given()
                    .header(identityHeader)
                    .when()
                    .contentType(ContentType.JSON)
                    .get(String.format("/endpoints/%s/history", ep.getString("id")))
                    .then()
                    .statusCode(200)
                    .extract().response();

            JsonArray histories = new JsonArray(response.getBody().asString());
            assertEquals(1, histories.size());

            for (int j = 0; j < histories.size(); j++) {
                JsonObject history = histories.getJsonObject(j);
                history.mapTo(NotificationHistory.class);
                // Sort first?
                if (ep.getString("name").startsWith("negative")) {
                    // TODO Validate that we actually reach this part
                    assertFalse(history.getBoolean("invocationResult"));
                    JsonObject attr = ep.getJsonObject("properties");
                    attr.mapTo(WebhookAttributes.class);

                    // Fetch details
                    response = given()
                            .header(identityHeader)
                            .when()
                            .contentType(ContentType.JSON)
                            .get(String.format("/endpoints/%s/history/%s/details", ep.getString("id"), history.getInteger("id")))
                            .then()
                            .statusCode(200)
                            .extract().response();

                    JsonObject json = new JsonObject(response.getBody().asString());
                    assertFalse(json.isEmpty());
                    assertEquals(400, json.getInteger("code").intValue());
                    assertEquals(attr.getString("url"), json.getString("url"));
                    assertEquals(attr.getString("method"), json.getString("method"));
                } else {
                    // TODO Validate that we actually reach this part
                    assertTrue(history.getBoolean("invocationResult"));
                }
            }
        }
    }

    @Test
    void t04_getUIDetailsAndUnlink() {
        Response response = given()
                .basePath(TestConstants.API_NOTIFICATIONS_V_1_0)
                .when()
                .header(identityHeader)
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .extract().response();

        JsonArray typesResponse = new JsonArray(response.getBody().asString());
        assertTrue(typesResponse.size() >= 1);

        JsonObject policiesAll = null;
        for (int i = 0; i < typesResponse.size(); i++) {
            JsonObject eventType = typesResponse.getJsonObject(i);
            eventType.mapTo(EventType.class);
            if (eventType.getString("name").equals(EVENT_TYPE_NAME) && eventType.getJsonObject("application").getString("name").equals(APP_NAME)) {
                policiesAll = eventType;
                break;
            }
        }

        assertNotNull(policiesAll);

        // Fetch the list
        response = given()
                .basePath(TestConstants.API_NOTIFICATIONS_V_1_0)
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get(String.format("/notifications/eventTypes/%s", policiesAll.getString("id")))
                .then()
                .statusCode(200)
                .extract().response();

        JsonArray endpoints = new JsonArray(response.getBody().asString());
        assertEquals(2, endpoints.size());

        for (int i = 0; i < endpoints.size(); i++) {
            JsonObject endpoint = endpoints.getJsonObject(i);
            endpoint.mapTo(Endpoint.class);
            String body =
                    given()
                            .basePath(TestConstants.API_NOTIFICATIONS_V_1_0)
                            .header(identityHeader)
                            .when()
                            .delete(String.format("/notifications/eventTypes/%s/%s", policiesAll.getString("id"), endpoint.getString("id")))
                            .then()
                            .statusCode(204)
                            .extract().body().asString();
            assertEquals(0, body.length());

        }

        // Fetch the list again
        response = given()
                .basePath(TestConstants.API_NOTIFICATIONS_V_1_0)
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get(String.format("/notifications/eventTypes/%s", policiesAll.getString("id")))
                .then()
                .statusCode(200)
                .extract().response();

        endpoints = new JsonArray(response.getBody().asString());
        assertEquals(0, endpoints.size());
    }

    @Test
    void t05_addEmptyDefaultSettings() {
        // Create default endpoint
        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.DEFAULT);
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

        JsonObject defaultEndpoint = new JsonObject(response.getBody().asString());
        defaultEndpoint.mapTo(Endpoint.class);
        assertNotNull(defaultEndpoint.getString("id"));

        // Get the eventTypeId
        response = given()
                .basePath(TestConstants.API_NOTIFICATIONS_V_1_0)
                .when()
                .header(identityHeader)
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .extract().response();

        JsonArray eventTypes = new JsonArray(response.getBody().asString());
        JsonObject targetType = null;
        for (int i = 0; i < eventTypes.size(); i++) {
            JsonObject eventType = eventTypes.getJsonObject(i);
            eventType.mapTo(EventType.class);
            if (eventType.getJsonObject("application").getString("name").equals(APP_NAME) && eventType.getString("name").equals(EVENT_TYPE_NAME)) {
                targetType = eventType;
            }
        }
        assertNotNull(targetType);

        // Link default to eventType
        given()
                .basePath(TestConstants.API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .put(String.format("/notifications/eventTypes/%s/%s", targetType.getString("id"), defaultEndpoint.getString("id")))
                .then()
                .statusCode(200);

        // Get existing endpoints
        // Link them to the default
    }

//    @Test
//    void t06_testEmptyDefaultTrigger() throws Exception {
//        // Send event there, expect it to be processed but nothing is sent and no error happens
//        InputStream is = getClass().getClassLoader().getResourceAsStream("input/platform.notifications.ingress.json");
//        String inputJson = IOUtils.toString(is, StandardCharsets.UTF_8);
//        ingressChan.send(inputJson);
//
//        // How do we check the process was completed and no errors appeared? We have no metrics yet
//
//        // Basically repeat t02_pushMessage
//        // The end result should be the same
//
//        // TODO Delete DefaultAttributes and that sort of stuff to ensure it doesn't mess up anything
//        // TODO Add Micrometer metrics here also and update Quarkus?
//    }

    @Test
    void t06_linkEndpointsAndTest() throws Exception {
        // Get the existing endpoints (not attached to anything)
        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/endpoints")
                .then()
                .statusCode(200)
                .extract().response();

        JsonArray endpoints = new JsonObject(response.getBody().asString()).getJsonArray("data");
        assertEquals(3, endpoints.size());

        for (int i = 0; i < endpoints.size(); i++) {
            JsonObject endpoint = endpoints.getJsonObject(i);
            endpoint.mapTo(Endpoint.class);
            if (endpoint.getString("type") != EndpointType.DEFAULT.name()) {
                given()
                        .basePath(TestConstants.API_NOTIFICATIONS_V_1_0)
                        .header(identityHeader)
                        .when()
                        .contentType(ContentType.JSON)
                        .put(String.format("/notifications/defaults/%s", endpoint.getString("id")))
                        .then()
                        .statusCode(200);
            }
        }

        t02_pushMessage();
    }

    @Test
    void t10_deleteBundle() {
        given()
                .basePath("/")
                .when()
                .delete("/internal/bundles/" + theBundle.getString("id"))
                .then()
                .statusCode(200);
    }
}
