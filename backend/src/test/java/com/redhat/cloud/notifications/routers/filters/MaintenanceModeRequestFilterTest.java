package com.redhat.cloud.notifications.routers.filters;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceModeRequestFilterTest {

    private final MaintenanceModeRequestFilter testee = new MaintenanceModeRequestFilter();

    @ParameterizedTest
    @ValueSource(strings = {"/blabla", "internal/admin/status"})
    void shouldBeAffectedByMaintenanceMode(String requestPath) {
        assertTrue(testee.isAffectedByMaintenanceMode(requestPath));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/health", "/metrics", "/internal", "/api/notifications/v1.0/status" })
    void shouldNotBeAffectedByMaintenanceModeWhenPathIsHealth(String requestPath) {
        assertFalse(testee.isAffectedByMaintenanceMode(requestPath));
    }
}
