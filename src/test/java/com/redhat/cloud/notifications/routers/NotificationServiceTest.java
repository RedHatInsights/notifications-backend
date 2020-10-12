package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.json.Json;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import javax.inject.Inject;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class NotificationServiceTest {
    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Inject
    ResourceHelpers helpers;

    @BeforeAll
    void init() {
        helpers.createTestAppAndEventTypes();
    }

//    @Test
    void testEventTypeFetching() {
        Response response = given()
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .extract().response();

        List<EventType> listResponse = Json.decodeValue(response.getBody().asString(), List.class);
        assertEquals(1, listResponse.size());

        EventType policiesAll = listResponse.get(0);
        assertNotNull(policiesAll.getId());
        assertNotNull(policiesAll.getApplication());
        assertNotNull(policiesAll.getApplication().getId());

    }
}
