package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.PoliciesParams;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockserver.model.HttpRequest;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.redhat.cloud.notifications.TestHelpers.serializeAction;
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

    private static final String APP_NAME = "PoliciesLifecycleTest";
    private static final String EVENT_TYPE_NAME = "All";

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_1_0;
    }

    private Header identityHeader;

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Channel("ingress")
    Emitter<String> ingressChan;

    @Inject
    EndpointResources resources;

    @Inject
    MeterRegistry meterRegistry;

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
    void t01_testAdding() {
        Application app = new Application();
        app.setName(APP_NAME);
        app.setDescription("The best app in the life");

        Response response = given()
                .when()
                .contentType(ContentType.JSON)
                .basePath("/")
                .body(Json.encode(app))
                .post("/applications")
                .then()
                .statusCode(200)
                .extract().response();

        Application appResponse = Json.decodeValue(response.getBody().asString(), Application.class);
        assertNotNull(appResponse.getId());

        // Create eventType
        EventType eventType = new EventType();
        eventType.setName(EVENT_TYPE_NAME);
        eventType.setDescription("Policies will take care of the rules");

        response = given()
                .when()
                .contentType(ContentType.JSON)
                .basePath("/")
                .body(Json.encode(eventType))
                .post(String.format("/applications/%s/eventTypes", appResponse.getId()))
                .then()
                .statusCode(200)
                .extract().response();

        EventType typeResponse = Json.decodeValue(response.getBody().asString(), EventType.class);
        assertNotNull(typeResponse.getId());

        // Add new endpoints
        WebhookAttributes webAttr = new WebhookAttributes();
        webAttr.setMethod(WebhookAttributes.HttpType.POST);
        webAttr.setDisableSSLVerification(true);
        webAttr.setSecretToken("super-secret-token");
        webAttr.setUrl(String.format("http://%s/test/lifecycle", mockServerConfig.getRunningAddress()));

        Endpoint ep = new Endpoint();
        ep.setType(Endpoint.EndpointType.WEBHOOK);
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

        Endpoint endpoint = Json.decodeValue(response.getBody().asString(), Endpoint.class);
        assertNotNull(endpoint.getId());

        webAttr = new WebhookAttributes();
        webAttr.setMethod(WebhookAttributes.HttpType.POST);
        webAttr.setDisableSSLVerification(true);
        webAttr.setUrl(String.format("http://%s/test/lifecycle", mockServerConfig.getRunningAddress()));

        ep = new Endpoint();
        ep.setType(Endpoint.EndpointType.WEBHOOK);
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

        Endpoint endpointFail = Json.decodeValue(response.getBody().asString(), Endpoint.class);
        assertNotNull(endpointFail.getId());

        // Link an eventType to endpoints

        for (Endpoint endpointLink : List.of(endpoint, endpointFail)) {
            given()
                    .basePath(TestConstants.API_NOTIFICATIONS_V_1_0)
                    .header(identityHeader)
                    .when()
                    .contentType(ContentType.JSON)
                    .put(String.format("/notifications/eventTypes/%d/%s", typeResponse.getId(), endpointLink.getId()))
                    .then()
                    .statusCode(200);
        }
    }

    @Test
    void t02_pushMessage() throws Exception {
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
        targetAction.setTimestamp(LocalDateTime.now());
        targetAction.setEventId(UUID.randomUUID().toString());
        targetAction.setEventType(EVENT_TYPE_NAME);
        targetAction.setTags(new ArrayList<>());
        targetAction.setParams(PoliciesParams.newBuilder().setTriggers(new HashMap<>()).build());

        Context context = new Context();
        context.setAccountId("tenant");
        Map<String, String> values = new HashMap<>();
        values.put("k", "v");
        values.put("k2", "v2");
        values.put("k3", "v");
        context.setMessage(values);
        targetAction.setEvent(context);

        String payload = serializeAction(targetAction);
        ingressChan.send(payload);

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
        Counter rejectedCount = meterRegistry.find("input.rejected").counter();
        assertEquals(0, rejectedCount.count());
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

        Endpoint[] endpoints = Json.decodeValue(response.getBody().asString(), Endpoint[].class);
        assertEquals(2, endpoints.length);

        for (Endpoint ep : endpoints) {
            // Fetch the notification history for the endpoints
            response = given()
                    .header(identityHeader)
                    .when()
                    .contentType(ContentType.JSON)
                    .get(String.format("/endpoints/%s/history", ep.getId().toString()))
                    .then()
                    .statusCode(200)
                    .extract().response();

            NotificationHistory[] histories = Json.decodeValue(response.getBody().asString(), NotificationHistory[].class);
            assertEquals(1, histories.length);

            for (NotificationHistory history : histories) {
                // Sort first?
                if (ep.getName().startsWith("negative")) {
                    // TODO Validate that we actually reach this part
                    assertFalse(history.isInvocationResult());
                    WebhookAttributes attr = (WebhookAttributes) ep.getProperties();

                    // Fetch details
                    response = given()
                            .header(identityHeader)
                            .when()
                            .contentType(ContentType.JSON)
                            .get(String.format("/endpoints/%s/history/%s/details", ep.getId().toString(), history.getId()))
                            .then()
                            .statusCode(200)
                            .extract().response();

                    JsonObject json = new JsonObject(response.getBody().asString());
                    assertFalse(json.isEmpty());
                    assertEquals(400, json.getInteger("code").intValue());
                    assertEquals(attr.getUrl(), json.getString("url"));
                    assertEquals(attr.getMethod().toString(), json.getString("method"));
                } else {
                    // TODO Validate that we actually reach this part
                    assertTrue(history.isInvocationResult());
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

        EventType[] typesResponse = Json.decodeValue(response.getBody().asString(), EventType[].class);
        assertTrue(typesResponse.length >= 1);

        EventType policiesAll = null;
        for (EventType eventType : typesResponse) {
            if (eventType.getName().equals(EVENT_TYPE_NAME) && eventType.getApplication().getName().equals(APP_NAME)) {
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
                .when().get(String.format("/notifications/eventTypes/%d", policiesAll.getId()))
                .then()
                .statusCode(200)
                .extract().response();

        Endpoint[] endpoints = Json.decodeValue(response.getBody().asString(), Endpoint[].class);
        assertEquals(2, endpoints.length);

        for (Endpoint endpoint : endpoints) {
            given()
                    .basePath(TestConstants.API_NOTIFICATIONS_V_1_0)
                    .header(identityHeader)
                    .when()
                    .delete(String.format("/notifications/eventTypes/%d/%s", policiesAll.getId(), endpoint.getId()))
                    .then()
                    .statusCode(200);
        }

        // Fetch the list again
        response = given()
                .basePath(TestConstants.API_NOTIFICATIONS_V_1_0)
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get(String.format("/notifications/eventTypes/%d", policiesAll.getId()))
                .then()
                .statusCode(200)
                .extract().response();

        endpoints = Json.decodeValue(response.getBody().asString(), Endpoint[].class);
        assertEquals(0, endpoints.length);
    }

    @Test
    void t05_addEmptyDefaultSettings() {
        // Create default endpoint
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

        Endpoint defaultEndpoint = Json.decodeValue(response.getBody().asString(), Endpoint.class);
        assertNotNull(defaultEndpoint.getId());

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

        EventType[] eventTypes = Json.decodeValue(response.getBody().asString(), EventType[].class);
        EventType targetType = null;
        for (EventType eventType : eventTypes) {
            if (eventType.getApplication().getName().equals(APP_NAME) && eventType.getName().equals(EVENT_TYPE_NAME)) {
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
                .put(String.format("/notifications/eventTypes/%d/%s", targetType.getId(), defaultEndpoint.getId()))
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

        Endpoint[] endpoints = Json.decodeValue(response.getBody().asString(), Endpoint[].class);
        assertEquals(3, endpoints.length);

        for (Endpoint endpoint : endpoints) {
            if (endpoint.getType() != Endpoint.EndpointType.DEFAULT) {
                given()
                        .basePath(TestConstants.API_NOTIFICATIONS_V_1_0)
                        .header(identityHeader)
                        .when()
                        .contentType(ContentType.JSON)
                        .put(String.format("/notifications/defaults/%s", endpoint.getId()))
                        .then()
                        .statusCode(200);
            }
        }

        t02_pushMessage();
    }
}
