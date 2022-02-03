package com.redhat.cloud.notifications.models.filter;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import java.util.UUID;

import static com.redhat.cloud.notifications.TestHelpers.createTurnpikeIdentityHeader;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ApiResponseFilterTest {

    @ConfigProperty(name = "internal-ui.admin-role")
    String adminRole;

    private Header turnpikeIdentityHeader() {
        return createTurnpikeIdentityHeader("admin", adminRole);
    }

    @Test
    void testActiveFilter() {
        String responseBody = given()
                .header(turnpikeIdentityHeader())
                .when().get("/internal/event-types/filtered")
                .then().statusCode(200)
                .extract().asString();
        JsonObject jsonEventType = new JsonObject(responseBody);
        jsonEventType.mapTo(EventType.class);
        assertNull(jsonEventType.getJsonObject("application"));
    }

    @Test
    void testInactiveFilter() {
        String responseBody = given()
                .header(turnpikeIdentityHeader())
                .when().get("/internal/event-types/unfiltered")
                .then().statusCode(200)
                .extract().asString();
        JsonObject jsonEventType = new JsonObject(responseBody);
        jsonEventType.mapTo(EventType.class);
        assertNotNull(jsonEventType.getJsonObject("application"));
    }

    @Path("/internal/event-types")
    public static class TestResources {

        @GET
        @Path("/filtered")
        public EventType returnFilteredEventType() {
            EventType eventType = buildEventType();
            eventType.filterOutApplication();
            return eventType;
        }

        @GET
        @Path("/unfiltered")
        public EventType returnUnfilteredEventType() {
            return buildEventType();
        }

        private EventType buildEventType() {
            Application app = new Application();
            app.setName("alpha");
            app.setDisplayName("bravo");
            app.setBundleId(UUID.randomUUID());
            EventType eventType = new EventType();
            eventType.setName("charlie");
            eventType.setDisplayName("delta");
            eventType.setApplication(app);
            return eventType;
        }
    }
}
