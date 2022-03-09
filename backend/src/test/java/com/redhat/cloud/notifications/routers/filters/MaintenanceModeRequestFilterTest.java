package com.redhat.cloud.notifications.routers.filters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class MaintenanceModeRequestFilterTest {

    private final MaintenanceModeRequestFilter testee = new MaintenanceModeRequestFilter();

    @Test
    void shouldBeAffectedByMaintenanceMode() {
        assertTrue(testee.isAffectedByMaintenanceMode("/blabla"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/health", "/metrics", "/internal", "/api/notifications/v1.0/status" })
    void shouldNotBeAffectedByMaintenanceModeWhenPathIsHealth(String requestPath) {
        assertFalse(testee.isAffectedByMaintenanceMode(requestPath));
    }
}
