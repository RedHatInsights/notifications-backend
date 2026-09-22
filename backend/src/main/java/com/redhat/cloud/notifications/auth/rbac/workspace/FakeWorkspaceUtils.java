package com.redhat.cloud.notifications.auth.rbac.workspace;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fake implementation of WorkspaceUtils for local testing and integration testing.
 * This bean replaces the real WorkspaceUtils when the "fake-rbac" profile is active.
 *
 * Usage:
 *   mvn quarkus:dev -Dquarkus.profile=fake-rbac
 *
 * Note: This extends WorkspaceUtils so CDI can use it as a replacement at injection
 * points. The parent class's @Inject fields will be injected but are not used by
 * this fake implementation.
 *
 * WARNING: This is a test utility and must be removed before production deployment.
 */
@ApplicationScoped
@Alternative
@Priority(1)
@IfBuildProfile("fake-rbac")
public class FakeWorkspaceUtils extends WorkspaceUtils {

    private static final Map<String, UUID> ORG_TO_WORKSPACE_MAP = new HashMap<>();

    static {
        // Predefined org-id to workspace-id mappings for testing
        ORG_TO_WORKSPACE_MAP.put("org-1", deterministicUUID("workspace-for-org-1"));
        ORG_TO_WORKSPACE_MAP.put("org-2", deterministicUUID("workspace-for-org-2"));
        ORG_TO_WORKSPACE_MAP.put("org-3", deterministicUUID("workspace-for-org-3"));
        ORG_TO_WORKSPACE_MAP.put("test-org-1", deterministicUUID("workspace-for-test-org-1"));
        ORG_TO_WORKSPACE_MAP.put("test-org-2", deterministicUUID("workspace-for-test-org-2"));
        ORG_TO_WORKSPACE_MAP.put("test-org-bootstrap-1", deterministicUUID("workspace-for-test-org-bootstrap-1"));
        ORG_TO_WORKSPACE_MAP.put("test-org-bootstrap-2", deterministicUUID("workspace-for-test-org-bootstrap-2"));
        ORG_TO_WORKSPACE_MAP.put("test-org-idempotent", deterministicUUID("workspace-for-test-org-idempotent"));
    }

    /**
     * Fake implementation that returns deterministic workspace UUIDs based on org-id.
     * If the org-id is in the predefined map, it returns the mapped UUID.
     * Otherwise, it generates a deterministic UUID based on the org-id string.
     *
     * Overrides the parent class method and does NOT call super or use injected
     * OAuth2/RBAC dependencies.
     */
    @Override
    public UUID getDefaultWorkspaceId(String orgId) {
        UUID workspaceId;

        if (ORG_TO_WORKSPACE_MAP.containsKey(orgId)) {
            workspaceId = ORG_TO_WORKSPACE_MAP.get(orgId);
        } else {
            // Generate deterministic UUID for unknown org-ids
            workspaceId = deterministicUUID("workspace-for-" + orgId);
        }

        Log.infof("[FAKE RBAC] org_id=%s -> workspace_id=%s", orgId, workspaceId);
        return workspaceId;
    }

    /**
     * Generates a deterministic UUID from a string (for testing purposes).
     * Same input always produces the same UUID.
     * Uses Java's UUID.nameUUIDFromBytes which is based on MD5 hashing.
     */
    private static UUID deterministicUUID(String input) {
        // Use UUID v3 (name-based MD5) for deterministic UUID generation
        return UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Add a custom org-id to workspace-id mapping at runtime (for tests).
     */
    public static void addMapping(String orgId, UUID workspaceId) {
        ORG_TO_WORKSPACE_MAP.put(orgId, workspaceId);
        Log.infof("[FAKE RBAC] Added mapping: org_id=%s -> workspace_id=%s", orgId, workspaceId);
    }

    /**
     * Get all configured mappings (for debugging).
     */
    public static Map<String, UUID> getAllMappings() {
        return new HashMap<>(ORG_TO_WORKSPACE_MAP);
    }
}
