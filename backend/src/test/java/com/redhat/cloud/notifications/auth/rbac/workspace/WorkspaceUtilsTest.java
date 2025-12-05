package com.redhat.cloud.notifications.auth.rbac.workspace;

import com.redhat.cloud.notifications.MockServerConfig;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

@QuarkusTest
public class WorkspaceUtilsTest {
    @Inject
    WorkspaceUtils workspaceUtils;

    /**
     * Make sure we clear the workspace path from the MockServer even if tests
     * fail.
     */
    @AfterAll
    public static void clearRbacWorkspaceRoutes() {
        MockServerConfig.clearRbacWorkspaces();
    }

    /**
     * Tests that a default workspace is fetched from RBAC and then cached for
     * a given organization.
     */
    @Test
    void testFetchDefaultWorkspace() {
        MockServerConfig.addMultipleReturningSingleDefaultWorkspaceRbacEndpoint();

        for (int i = 0; i < 5; i++) {
            final UUID workspaceId = this.workspaceUtils.getDefaultWorkspaceId(DEFAULT_ORG_ID);
            Assertions.assertNotNull(workspaceId, "a workspace ID should have been returned from the function under test");
        }

        MockServerConfig.verifyDefaultWorkspaceFetchedOnlyOnce();
        MockServerConfig.clearRbacWorkspaces();
    }

    /**
     * Tests that when more than one workspace is received, or the single
     * received workspace is not the default one, an exception is raised.
     */
    @Test
    void testInvalidNonDefaultWorkspacesReturnedThrowsException() {
        MockServerConfig.addMissingCountFromWorkspacesResponseRbacEndpoint();

        Assertions.assertThrows(
            UnauthorizedException.class,
            () -> this.workspaceUtils.getDefaultWorkspaceId(DEFAULT_ORG_ID)
        );

        MockServerConfig.clearRbacWorkspaces();
        MockServerConfig.addNoReturnedWorkspacesResponseRbacEndpoint();

        Assertions.assertThrows(
            UnauthorizedException.class,
            () -> this.workspaceUtils.getDefaultWorkspaceId(DEFAULT_ORG_ID)
        );

        MockServerConfig.clearRbacWorkspaces();
        MockServerConfig.addMultipleReturningMultipleWorkspacesRbacEndpoint();

        Assertions.assertThrows(
            UnauthorizedException.class,
            () -> this.workspaceUtils.getDefaultWorkspaceId(DEFAULT_ORG_ID)
        );

        MockServerConfig.clearRbacWorkspaces();
        MockServerConfig.addReturningSingleRootWorkspaceRbacEndpoint();

        Assertions.assertThrows(
            UnauthorizedException.class,
            () -> this.workspaceUtils.getDefaultWorkspaceId(DEFAULT_ORG_ID)
        );

        MockServerConfig.clearRbacWorkspaces();
    }
}
