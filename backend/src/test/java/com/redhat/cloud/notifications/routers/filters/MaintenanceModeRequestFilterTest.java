package com.redhat.cloud.notifications.routers.filters;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MaintenanceModeRequestFilterTest {

    @Inject
    MaintenanceModeRequestFilter requestFilter;

    @ParameterizedTest
    @ValueSource(strings = {"/blabla", "/api/notifications/v1.0/notifications/eventTypes"})
    void shouldBeAffectedByMaintenanceMode(String requestPath) {
        assertTrue(requestFilter.isAffectedByMaintenanceMode(requestPath));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/health", "/metrics", "/internal", "/internal/admin/status", "/api/notifications/v1.0/status"})
    void shouldNotBeAffectedByMaintenanceModeWhenPathIsHealth(String requestPath) {
        assertFalse(requestFilter.isAffectedByMaintenanceMode(requestPath));
    }
}
