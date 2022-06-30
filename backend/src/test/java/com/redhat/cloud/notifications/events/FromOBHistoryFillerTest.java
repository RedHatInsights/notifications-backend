package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.repositories.NotificationRepository;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.http.Header;
import org.junit.jupiter.api.BeforeAll;
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
class FromOBHistoryFillerTest {

    private static final String BASE = "src/test/resources/";
    public static final String ORIGINAL_ID = "92c05a9b-237c-4fe5-bb42-afab9fb485b8";
    private static final String OUTCOME = "Filters of processor did not match for event";
    public static final String OB_ERROR_HANDLER = Constants.API_NOTIFICATIONS_V_1_0 + "/ob/errors";

    static Header adminIdentity;

    @InjectMock
    NotificationRepository repo;

    @BeforeAll
    static void init() {
        String tmp = TestHelpers.encodeRHIdentityInfo("user", "123", "ob-user");
        MockServerConfig.addMockRbacAccess(tmp, MockServerConfig.RbacAccess.FULL_ACCESS);
        adminIdentity = TestHelpers.createRHIdentityHeader(tmp);
    }

    @Test
    void goodHistoryUpdate() throws Exception {
        JsonObject jo = readValueFromFile("ob-backchannel.json");
        String body = jo.toString();

        Map<String, Object> detailMap = new HashMap<>();
        detailMap.put("historyId", ORIGINAL_ID);
        detailMap.put("successful", false);
        detailMap.put("details", OUTCOME);
        detailMap.put("duration", 0);

        when(repo.updateHistoryItem(anyMap())).thenReturn(true);

        given()
                .body(body)
                .header("x-rhose-original-event-id", ORIGINAL_ID)
                .header(adminIdentity)
                .contentType("application/json")
                .when()
                .post(OB_ERROR_HANDLER)
                .then()
                .statusCode(202);

        verify(repo, times(1)).updateHistoryItem(detailMap);
    }

    @Test
    void unknownOriginalId() throws Exception {
        JsonObject jo = readValueFromFile("ob-backchannel.json");
        String body = jo.toString();

        when(repo.updateHistoryItem(anyMap())).thenThrow(new NoResultException());

        given()
                .body(body)
                .header("x-rhose-original-event-id", "b8a1ac39-cd30-4f5a-b6cf-bd72d7c3ce6e")
                .header(adminIdentity)
                .contentType("application/json")
                .when()
                .post(OB_ERROR_HANDLER)
                .then()
                .statusCode(400);

    }

    @Test
    void badOriginalId() throws Exception {
        JsonObject jo = readValueFromFile("ob-backchannel.json");
        String body = jo.toString();

        given()
                .body(body)
                .header("x-rhose-original-event-id", "92c05a9b")
                .header(adminIdentity)
                .contentType("application/json")
                .when()
                .post(OB_ERROR_HANDLER)
                .then()
                .statusCode(400);
    }

    @Test
    void badCaller() throws Exception {
        JsonObject jo = readValueFromFile("ob-backchannel.json");
        String body = jo.toString();

        String tmp = TestHelpers.encodeRHIdentityInfo("user", "123", "someone-else");
        MockServerConfig.addMockRbacAccess(tmp, MockServerConfig.RbacAccess.FULL_ACCESS);
        Header someIdentity = TestHelpers.createRHIdentityHeader(tmp);

        given()
                .body(body)
                .header("x-rhose-original-event-id", ORIGINAL_ID)
                .header(someIdentity)
                .contentType("application/json")
                .when()
                .post(OB_ERROR_HANDLER)
                .then()
                .statusCode(403);
    }

    @Test
    void noOriginalIdPassed() throws Exception {
        JsonObject jo = readValueFromFile("ob-backchannel.json");
        String body = jo.toString();

        given()
                .header(adminIdentity)
                .body(body)
                .contentType("application/json")
                .when()
                .post(OB_ERROR_HANDLER)
                .then()
                .statusCode(400);
    }

    @Test
    void noBodyPassed() throws Exception {

        given()
                .header("x-rhose-original-event-id", ORIGINAL_ID)
                .header(adminIdentity)
                .contentType("application/json")
                .when()
                .post(OB_ERROR_HANDLER)
                .then()
                .statusCode(400);
    }

    @Test
    void bodyIsNoCloudEvent() {
        String body = "lilalu";

        given()
                .body(body)
                .header("x-rhose-original-event-id", ORIGINAL_ID)
                .header(adminIdentity)
                .contentType("application/json")
                .when()
                .post(OB_ERROR_HANDLER)
                .then()
                .statusCode(400);
    }

    private JsonObject readValueFromFile(String fileName) throws IOException {
        try (InputStream is = new FileInputStream(BASE + fileName)) {
            return Json.createReader(is).readValue().asJsonObject();
        }
    }

}
