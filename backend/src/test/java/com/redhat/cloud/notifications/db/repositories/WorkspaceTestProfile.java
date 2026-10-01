package com.redhat.cloud.notifications.db.repositories;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile for workspace tests.
 * Uses fake-rbac profile to activate FakeWorkspaceUtils but re-enables
 * dev services so Testcontainers can provide PostgreSQL for tests.
 */
public class WorkspaceTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        // Re-enable dev services for tests (fake-rbac profile disables them for local dev)
        return Map.of(
            "quarkus.devservices.enabled", "true",
            "quarkus.unleash.devservices.enabled", "true"
        );
    }

    @Override
    public String getConfigProfile() {
        return "fake-rbac";
    }
}
