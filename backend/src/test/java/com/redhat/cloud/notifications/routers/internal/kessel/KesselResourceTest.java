package com.redhat.cloud.notifications.routers.internal.kessel;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.auth.kessel.OAuth2ClientCredentialsCache;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
public class KesselResourceTest extends DbIsolatedTest {

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @InjectMock
    OAuth2ClientCredentialsCache oauth2ClientCredentialsCache;

    @Test
    void testClearCacheEndpointWithAdminRole() {

        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("admin", adminRole);

        given()
            .basePath(API_INTERNAL)
            .header(adminIdentity)
            .when().delete("/kessel/oauth2-client-credentials/clear-cache")
            .then().statusCode(NO_CONTENT.getStatusCode());

        verify(oauth2ClientCredentialsCache, times(1)).clearCache();
    }

    @Test
    void testClearCacheEndpointUnauthorized() {

        Header userIdentity = TestHelpers.createTurnpikeIdentityHeader("user", "regular-user");

        given()
            .basePath(API_INTERNAL)
            .header(userIdentity)
            .when().delete("/kessel/oauth2-client-credentials/clear-cache")
            .then().statusCode(FORBIDDEN.getStatusCode());

        verify(oauth2ClientCredentialsCache, times(0)).clearCache();
    }
}
