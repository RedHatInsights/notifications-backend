package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class ValidationResourceTest {

    @InjectMock
    ApplicationRepository applicationRepository;

    @Test
    void shouldReturn400WhenTripleIsInvalid() {
        when(applicationRepository.getEventType(anyString(), anyString(), anyString())).thenReturn(null);

        final String response = given()
                .when()
                .param("bundle", "blabla")
                .param("application", "Notifications")
                .param("eventType", "Any")
                .get(API_INTERNAL + "/validation/baet")
                .then()
                .statusCode(400)
                .extract().asString();

        assertEquals("No event type found for [bundle=blabla, application=Notifications, eventType=Any]", response);
    }

    @Test
    void shouldReturn200WhenTripleExists() {
        EventType eventType = new EventType();
        when(applicationRepository.getEventType(eq("my-bundle"), eq("Policies"), eq("Any"))).thenReturn(eventType);

        given()
                .when()
                .param("bundle", "my-bundle")
                .param("application", "Policies")
                .param("eventType", "Any")
                .get(API_INTERNAL + "/validation/baet")
                .then()
                .statusCode(200);
    }

}
