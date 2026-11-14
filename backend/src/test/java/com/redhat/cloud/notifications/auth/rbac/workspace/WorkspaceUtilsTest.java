package com.redhat.cloud.notifications.auth.rbac.workspace;

import com.redhat.cloud.notifications.auth.kessel.OAuth2ClientCredentialsCache;
import com.redhat.cloud.notifications.config.BackendConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.project_kessel.api.auth.OAuth2AuthRequest;
import org.project_kessel.api.auth.OAuth2ClientCredentials;
import org.project_kessel.api.rbac.v2.Workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceUtilsTest {

    private static final String ORG_ID = "test-org-id";
    private static final String RBAC_URL = "http://localhost:8080";

    private WorkspaceUtils workspaceUtils;
    private OAuth2ClientCredentialsCache credentialsCache;
    private BackendConfig backendConfig;

    @BeforeEach
    void setUp() {
        workspaceUtils = Mockito.spy(new WorkspaceUtils());
        credentialsCache = mock(OAuth2ClientCredentialsCache.class);
        backendConfig = mock(BackendConfig.class);

        workspaceUtils.oauth2ClientCredentialsCache = credentialsCache;
        workspaceUtils.backendConfig = backendConfig;
        workspaceUtils.workspaceLookupEnabled = true;

        OAuth2ClientCredentials credentials = mock(OAuth2ClientCredentials.class);
        when(credentialsCache.getCredentials()).thenReturn(credentials);
        when(backendConfig.getRbacUrl()).thenReturn(RBAC_URL);
    }

    @Test
    void testDefaultWorkspaceIdWhenLookupDisabled() {
        workspaceUtils.workspaceLookupEnabled = false;

        UUID expectedWorkspaceId = UUID.nameUUIDFromBytes(
            ("workspace-for-" + ORG_ID).getBytes(StandardCharsets.UTF_8)
        );

        UUID actualWorkspaceId = workspaceUtils.getDefaultWorkspaceId(ORG_ID);

        assertNotNull(actualWorkspaceId);
        assertEquals(expectedWorkspaceId, actualWorkspaceId);
    }

    @Test
    void testDefaultWorkspaceIdWhenRbacReturnsNullWorkspace() throws IOException, InterruptedException {
        Mockito.doReturn(null)
            .when(workspaceUtils)
            .fetchWorkspace(eq(RBAC_URL), eq(ORG_ID), any(OAuth2AuthRequest.class));

        UUID actualWorkspaceId = workspaceUtils.getDefaultWorkspaceId(ORG_ID);

        assertNull(actualWorkspaceId);
    }

    @Test
    void testDefaultWorkspaceIdWhenRbacReturnsWorkspaceWithNullId() throws IOException, InterruptedException {
        Workspace workspace = mock(Workspace.class);
        when(workspace.getId()).thenReturn(null);

        Mockito.doReturn(workspace)
            .when(workspaceUtils)
            .fetchWorkspace(eq(RBAC_URL), eq(ORG_ID), any(OAuth2AuthRequest.class));

        UUID actualWorkspaceId = workspaceUtils.getDefaultWorkspaceId(ORG_ID);

        assertNull(actualWorkspaceId);
    }

    @Test
    void testDefaultWorkspaceIdWhenRbacReturnsWorkspaceWithEmptyId() throws IOException, InterruptedException {
        Workspace workspace = mock(Workspace.class);
        when(workspace.getId()).thenReturn("");

        Mockito.doReturn(workspace)
            .when(workspaceUtils)
            .fetchWorkspace(eq(RBAC_URL), eq(ORG_ID), any(OAuth2AuthRequest.class));

        UUID actualWorkspaceId = workspaceUtils.getDefaultWorkspaceId(ORG_ID);

        assertNull(actualWorkspaceId);
    }

    @Test
    void testDefaultWorkspaceIdWhenRbacReturnsValidWorkspace() throws IOException, InterruptedException {
        UUID expectedWorkspaceId = UUID.randomUUID();
        Workspace workspace = mock(Workspace.class);
        when(workspace.getId()).thenReturn(expectedWorkspaceId.toString());

        Mockito.doReturn(workspace)
            .when(workspaceUtils)
            .fetchWorkspace(eq(RBAC_URL), eq(ORG_ID), any(OAuth2AuthRequest.class));

        UUID actualWorkspaceId = workspaceUtils.getDefaultWorkspaceId(ORG_ID);

        assertNotNull(actualWorkspaceId);
        assertEquals(expectedWorkspaceId, actualWorkspaceId);
    }
}
