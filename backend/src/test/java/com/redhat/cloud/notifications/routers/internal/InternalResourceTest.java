package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import java.time.LocalTime;
import java.time.ZoneOffset;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.TestHelpers.createTurnpikeIdentityHeader;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class InternalResourceTest extends DbIsolatedTest {

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Test
    public void testDailyDigestTimePreference() {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("tenant", "empty", "username");
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        given()
            .basePath(API_INTERNAL)
            .header(createTurnpikeIdentityHeader("admin", adminRole))
            .when()
            .get("/daily-digest/time-preference/1234")
            .then()
            .statusCode(404)
            .contentType(JSON);

        LocalTime localTime = LocalTime.now(ZoneOffset.UTC).withNano(0);
        given()
            .basePath(API_INTERNAL)
            .header(createTurnpikeIdentityHeader("admin", adminRole))
            .when()
            .contentType(JSON)
            .body(localTime)
            .put("/daily-digest/time-preference/1234")
            .then()
            .statusCode(200);

        LocalTime storedLocalTime = given()
            .basePath(API_INTERNAL)
            .header(createTurnpikeIdentityHeader("admin", adminRole))
            .when()
            .get("/daily-digest/time-preference/1234")
            .then()
            .statusCode(200)
            .contentType(JSON).extract().as(LocalTime.class);

        assertEquals(localTime, storedLocalTime);

        // test insufficient privileges
        given()
            .basePath(API_INTERNAL)
            .header(createTurnpikeIdentityHeader("admin", "none"))
            .when()
            .contentType(JSON)
            .body(localTime)
            .put("/daily-digest/time-preference/1234")
            .then()
            .statusCode(403);
    }
}
