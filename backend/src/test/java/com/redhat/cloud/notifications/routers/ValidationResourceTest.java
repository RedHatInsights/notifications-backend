package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.http.Header;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.NoResultException;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.FULL_ACCESS;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class ValidationResourceTest {

    @InjectMock
    ApplicationRepository applicationRepository;

    @Inject
    FeatureFlipper featureFlipper;

    @Test
    void shouldReturnNotFoundWhenTripleIsInvalid_AccountId() {
        featureFlipper.setUseOrgId(false);
        shouldReturnNotFoundWhenTripleIsInvalid();
    }

    @Test
    void shouldReturnNotFoundWhenTripleIsInvalid_OrgId() {
        featureFlipper.setUseOrgId(true);
        shouldReturnNotFoundWhenTripleIsInvalid();
        featureFlipper.setUseOrgId(false);
    }

    void shouldReturnNotFoundWhenTripleIsInvalid() {
        when(applicationRepository.getEventType(eq("blabla"), eq("Notifications"), eq("Any"))).thenThrow(new NoResultException());

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("empty", "empty", "user");
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        final String response = given()
                .header(identityHeader)
                .when()
                .param("bundle", "blabla")
                .param("application", "Notifications")
                .param("eventType", "Any")
                .get(API_INTERNAL + "/validation/baet")
                .then()
                .statusCode(404)
                .extract().asString();

        assertEquals("No event type found for [bundle=blabla, application=Notifications, eventType=Any]", response);
    }

    @Test
    void shouldReturnStatusOkWhenTripleExists_AccountId() {
        featureFlipper.setUseOrgId(false);
        shouldReturnStatusOkWhenTripleExists();
    }

    @Test
    void shouldReturnStatusOkWhenTripleExists_OrgId() {
        featureFlipper.setUseOrgId(true);
        shouldReturnStatusOkWhenTripleExists();
        featureFlipper.setUseOrgId(false);
    }

    void shouldReturnStatusOkWhenTripleExists() {
        EventType eventType = new EventType();
        when(applicationRepository.getEventType(eq("my-bundle"), eq("Policies"), eq("Any"))).thenReturn(eventType);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("empty", "empty", "user");
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        given()
                .header(identityHeader)
                .when()
                .param("bundle", "my-bundle")
                .param("application", "Policies")
                .param("eventType", "Any")
                .get(API_INTERNAL + "/validation/baet")
                .then()
                .statusCode(200);
    }

}
