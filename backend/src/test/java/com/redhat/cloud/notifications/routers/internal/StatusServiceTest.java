package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.restassured.http.Header;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.Constants.API_INTERNAL_NOTIFICATIONS_BACKEND;
import static com.redhat.cloud.notifications.TestConstants.API_INTEGRATIONS_V_1_0;
import static com.redhat.cloud.notifications.TestConstants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.TestHelpers.createTurnpikeIdentityHeader;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class StatusServiceTest extends DbIsolatedTest {

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @InjectSpy
    BackendConfig backendConfig;

    @CacheName("maintenance")
    Cache maintenance;

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    public void testValidCurrentStatus(String basePath) {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("tenant", "empty", "username");
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        // The test must not be run with a cached status from another test.
        clearCachedStatus();

        /*
         * First, let's check that all APIs are available.
         * We won't test /health because it's always DOWN and returns 503 during tests.
         */
        getMetrics();
        getBundles(basePath);
        getEndpoints(identityHeader, 200);
        getEventTypes(identityHeader, 200);

        // Let's change that to MAINTENANCE
        when(backendConfig.isMaintenanceModeEnabled(anyString())).thenReturn(true);

        // The cache is cleared again because we don't want to wait for the normal expiration delay.
        clearCachedStatus();

        // The maintenance is on but /internal and /metrics should still be available.
        getMetrics();
        getBundles(basePath);

        // On the other hand, /api/integrations and /api/notifications should return 503.
        getEndpoints(identityHeader, 503);
        getEventTypes(identityHeader, 503);

        // We don't want other tests to be run with maintenance mode on.
        when(backendConfig.isMaintenanceModeEnabled(anyString())).thenReturn(false);
        clearCachedStatus();
    }

    void clearCachedStatus() {
        maintenance.invalidateAll().await().indefinitely();
    }

    private void getMetrics() {
        given()
                .when()
                .get("/metrics")
                .then()
                .statusCode(200);
    }

    private void getBundles(String basePath) {
        given()
                .basePath(basePath)
                .header(createTurnpikeIdentityHeader("admin", adminRole))
                .when()
                .get("/bundles")
                .then()
                .statusCode(200)
                .contentType(JSON);
    }

    private void getEndpoints(Header identityHeader, int expectedStatusCode) {
        given()
                .basePath(API_INTEGRATIONS_V_1_0)
                .header(identityHeader)
                .when()
                .get("/endpoints")
                .then()
                .statusCode(expectedStatusCode);
    }

    private void getEventTypes(Header identityHeader, int expectedStatusCode) {
        given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .when()
                .get("/notifications/eventTypes")
                .then()
                .statusCode(expectedStatusCode);
    }
}
