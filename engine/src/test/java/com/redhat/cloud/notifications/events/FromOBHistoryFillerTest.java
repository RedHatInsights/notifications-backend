package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.junit.jupiter.api.Test;

import javax.json.Json;
import javax.json.JsonObject;
import javax.persistence.NoResultException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test the (error) back channel from OpenBridge
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class FromOBHistoryFillerTest {

    private static final String BASE = "src/test/resources/";
    public static final String ORIGINAL_ID = "92c05a9b-237c-4fe5-bb42-afab9fb485b8";
    private static final String OUTCOME = "Filters of processor did not match for event ";

    @InjectMock
    NotificationHistoryRepository repo;

    @Test
    void testHistoryUpdate() throws Exception {
        JsonObject jo = readValueFromFile("ob-backchannel.json");
        String body = jo.toString();

        Map<String,Object> detailMap = new HashMap<>();
        detailMap.put("historyId", ORIGINAL_ID);
        detailMap.put("details", new HashMap<>());
        detailMap.put("successful", false);
        detailMap.put("outcome", OUTCOME);

        when(repo.updateHistoryItem(anyMap())).thenReturn(true);

        given()
                .body(body)
                .header("x-rhose-original-event-id", ORIGINAL_ID)
                .contentType("application/json")
                .when()
                .post("/ob/errorHandler")
                .then()
                .statusCode(202);

        verify(repo, times(1)).updateHistoryItem(detailMap);
    }

    @Test
    void testUnknownId() throws Exception {
        JsonObject jo = readValueFromFile("ob-backchannel.json");
        String body = jo.toString();

        when(repo.updateHistoryItem(anyMap())).thenThrow(new NoResultException());

        given()
                .body(body)
                .header("x-rhose-original-event-id", "b8a1ac39-cd30-4f5a-b6cf-bd72d7c3ce6e")
                .contentType("application/json")
                .when()
                .post("/ob/errorHandler")
                .then()
                .statusCode(400);

    }

    @Test
    public void testBadOrigId() throws Exception {
        JsonObject jo = readValueFromFile("ob-backchannel.json");
        String body = jo.toString();

        given()
                .body(body)
                .header("x-rhose-original-event-id", "92c05a9b")
                .contentType("application/json")
                .when()
                .post("/ob/errorHandler")
                .then()
                .statusCode(400);
    }

    @Test
    void testNoOrigId() throws Exception {
        JsonObject jo = readValueFromFile("ob-backchannel.json");
        String body = jo.toString();

        given()
                .body(body)
                .contentType("application/json")
                .when()
                .post("/ob/errorHandler")
                .then()
                .statusCode(400);
    }

    @Test
    void testNoBody() throws Exception {

        given()
                .header("x-rhose-original-event-id", ORIGINAL_ID)
                .contentType("application/json")
                .when()
                .post("/ob/errorHandler")
                .then()
                .statusCode(400);
    }

    @Test
    void testBodyNoCE() throws Exception {
        String body = "lilalu";

        given()
                .body(body)
                .header("x-rhose-original-event-id", ORIGINAL_ID)
                .contentType("application/json")
                .when()
                .post("/ob/errorHandler")
                .then()
                .statusCode(400);
    }

    private JsonObject readValueFromFile(String fileName) throws IOException {
        try (InputStream is = new FileInputStream(BASE + fileName)) {
            return Json.createReader(is).readValue().asJsonObject();
        }
    }

}
