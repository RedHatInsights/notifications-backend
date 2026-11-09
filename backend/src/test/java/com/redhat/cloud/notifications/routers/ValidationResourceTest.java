package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.Constants.API_INTERNAL_NOTIFICATIONS_BACKEND;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class ValidationResourceTest {

    @InjectMock
    ApplicationRepository applicationRepository;

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void shouldReturn400WhenTripleIsInvalid(String basePath) {
        when(applicationRepository.getEventType(anyString(), anyString(), anyString())).thenReturn(null);

        final String response = given()
                .when()
                .param("bundle", "blabla")
                .param("application", "Notifications")
                .param("eventType", "Any")
                .get(basePath + "/validation/baet")
                .then()
                .statusCode(400)
                .extract().asString();

        assertEquals("No event type found for [bundle=blabla, application=Notifications, eventType=Any]", response);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void shouldReturn200WhenTripleExists(String basePath) {
        EventType eventType = new EventType();
        when(applicationRepository.getEventType(eq("my-bundle"), eq("Policies"), eq("Any"))).thenReturn(eventType);

        given()
                .when()
                .param("bundle", "my-bundle")
                .param("application", "Policies")
                .param("eventType", "Any")
                .get(basePath + "/validation/baet")
                .then()
                .statusCode(200);
    }

}
