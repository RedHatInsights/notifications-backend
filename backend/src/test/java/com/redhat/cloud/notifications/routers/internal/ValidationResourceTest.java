package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.Header;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.Constants.API_INTERNAL_NOTIFICATIONS_BACKEND;
import static com.redhat.cloud.notifications.CrudTestHelpers.createApp;
import static com.redhat.cloud.notifications.CrudTestHelpers.createBundle;
import static com.redhat.cloud.notifications.CrudTestHelpers.createEventType;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class ValidationResourceTest extends DbIsolatedTest {


    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testGetBaetList(String basePath) {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);

        final String bundleName = "bundle-name-baet1";
        final String applicationName = "application-name-baet1";
        String bundleId = createBundle(basePath, adminIdentity, bundleName, RandomStringUtils.secure().nextAlphanumeric(10), OK.getStatusCode()).get();
        String applicationId = createApp(basePath, adminIdentity, bundleId, applicationName, RandomStringUtils.secure().nextAlphanumeric(10), null, OK.getStatusCode()).get();
        EventType eventType = new EventType();
        eventType.setDescription(RandomStringUtils.secure().nextAlphanumeric(10));
        eventType.setName("event1");
        eventType.setDisplayName("Event 1");
        eventType.setApplicationId(UUID.fromString(applicationId));
        createEventType(basePath, adminIdentity, eventType, OK.getStatusCode());

        eventType.setName("event2");
        eventType.setDisplayName("Event 2");
        createEventType(basePath, adminIdentity, eventType, OK.getStatusCode());

        Map<String, Map<String, List<String>>> baetMap = given()
            .header(adminIdentity)
            .contentType(JSON)
            .when()
            .get(basePath + "/validation/baet_list")
            .then()
            .statusCode(OK.getStatusCode()).extract().as(new TypeRef<>() { });

        assertEquals(2, baetMap.size());
        assertEquals(1, baetMap.get("rhel").size());
        assertEquals(1, baetMap.get("rhel").get("advisor").size());
        assertTrue(baetMap.get("rhel").get("advisor").contains("new-recommendation"));

        assertEquals(1, baetMap.get(bundleName).size());
        assertEquals(2, baetMap.get(bundleName).get(applicationName).size());
        assertTrue(baetMap.get(bundleName).get(applicationName).contains("event1"));
        assertTrue(baetMap.get(bundleName).get(applicationName).contains("event2"));
    }
}
