package com.redhat.cloud.notifications.db.repositories;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile for workspace tests.
 * Uses fake-rbac to avoid complex OIDC/RBAC mocking.
 */
public class WorkspaceTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.test.profile", "fake-rbac"
        );
    }

    @Override
    public String getConfigProfile() {
        return "fake-rbac";
    }
}
