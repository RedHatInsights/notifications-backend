package com.redhat.cloud.notifications.models;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class WorkspaceTest {

    @Test
    void testWorkspaceCreationWithOrgId() {
        Workspace workspace = new Workspace();
        workspace.setId(UUID.randomUUID());
        workspace.setOrgId("test-org-123");

        assertNotNull(workspace.getId());
        assertEquals("test-org-123", workspace.getOrgId());
    }

    @Test
    void testSystemWorkspaceCreation() {
        Workspace workspace = new Workspace();
        workspace.setId(UUID.randomUUID());
        workspace.setOrgId(null);

        assertNotNull(workspace.getId());
        assertNull(workspace.getOrgId());
    }

    @Test
    void testEqualsAndHashCode() {
        UUID id = UUID.randomUUID();

        Workspace workspace1 = new Workspace();
        workspace1.setId(id);
        workspace1.setOrgId("org1");

        Workspace workspace2 = new Workspace();
        workspace2.setId(id);
        workspace2.setOrgId("org2");

        assertEquals(workspace1, workspace2); // Same ID = equal
        assertEquals(workspace1.hashCode(), workspace2.hashCode());
    }
}
