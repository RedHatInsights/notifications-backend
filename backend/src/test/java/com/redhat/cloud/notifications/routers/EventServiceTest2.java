package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.MockServerClientConfig.RbacAccess;
import static com.redhat.cloud.notifications.MockServerClientConfig.RbacAccess.NOTIFICATIONS_ACCESS_ONLY;
import static com.redhat.cloud.notifications.MockServerClientConfig.RbacAccess.WRONG_ACCESS;
import static com.redhat.cloud.notifications.routers.EventService.PATH;
import static io.restassured.RestAssured.given;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventServiceTest2 extends DbIsolatedTest {

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Test
    void shouldNotBeAllowedToGetEventLogsWhenUserHasWrongAccessRights() {
        Header noAccessIdentityHeader = mockRbac(WRONG_ACCESS);
        given()
                .header(noAccessIdentityHeader)
                .when().get(PATH)
                .then()
                .statusCode(403);
    }

    @Test
    void shouldNotBeAllowedTogetEventLogsWhenUserHasNotificationsAccessRightsOnly() {
        Header noAccessIdentityHeader = mockRbac(NOTIFICATIONS_ACCESS_ONLY);
        given()
                .header(noAccessIdentityHeader)
                .when().get(PATH)
                .then()
                .statusCode(403);
    }

    private Header mockRbac(RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeIdentityInfo("tenant", "user");
        mockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createIdentityHeader(identityHeaderValue);
    }

}
