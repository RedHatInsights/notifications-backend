package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.http.Header;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import javax.persistence.NoResultException;

import static com.redhat.cloud.notifications.Constants.INTERNAL;
import static com.redhat.cloud.notifications.MockServerClientConfig.RbacAccess.FULL_ACCESS;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class ValidationEndpointTest {

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @InjectMock
    ApplicationResources appResources;

    @Test
    void shouldReturnNotFoundWhenTripleIsInvalid() {
        when(appResources.getEventType(eq("blabla"), eq("Notifications"), eq("Any"))).thenThrow(NoResultException.class);

        String identityHeaderValue = TestHelpers.encodeIdentityInfo("empty", "user");
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        final String response = given()
                .header(identityHeader)
                .when()
                .param("bundle", "blabla")
                .param("application", "Notifications")
                .param("eventType", "Any")
                .get(INTERNAL + "/validation")
                .then()
                .statusCode(500)
                .extract().asString();

        assertEquals("did not find triple of bundle", response);
    }

    @Test
    void shouldReturnStatusOkWhenTripleExists() {
        EventType eventType = new EventType();
        when(appResources.getEventType(eq("my-bundle"), eq("Policies"), eq("Any"))).thenReturn(
                Uni.createFrom().item(eventType)
        );

        String identityHeaderValue = TestHelpers.encodeIdentityInfo("empty", "user");
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        given()
                .header(identityHeader)
                .when()
                .param("bundle", "my-bundle")
                .param("application", "Policies")
                .param("eventType", "Any")
                .get(INTERNAL + "/validation")
                .then()
                .statusCode(200);
    }

}
