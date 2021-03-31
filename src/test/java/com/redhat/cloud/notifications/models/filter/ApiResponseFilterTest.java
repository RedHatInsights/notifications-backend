package com.redhat.cloud.notifications.models.filter;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ApiResponseFilterTest {

    @Test
    void testActiveFilter() {
        EventType eventType = given()
                .when().get("/internal/event-types/filtered")
                .then().statusCode(200)
                .extract().as(EventType.class);
        assertNull(eventType.getApplication());
    }

    @Test
    void testInactiveFilter() {
        EventType eventType = given()
                .when().get("/internal/event-types/unfiltered")
                .then().statusCode(200)
                .extract().as(EventType.class);
        assertNotNull(eventType.getApplication());
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
