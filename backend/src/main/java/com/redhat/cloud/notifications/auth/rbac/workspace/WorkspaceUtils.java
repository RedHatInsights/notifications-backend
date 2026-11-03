package com.redhat.cloud.notifications.auth.rbac.workspace;

import com.redhat.cloud.notifications.auth.kessel.OAuth2ClientCredentialsCache;
import com.redhat.cloud.notifications.config.BackendConfig;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.project_kessel.api.auth.OAuth2AuthRequest;
import org.project_kessel.api.auth.OAuth2ClientCredentials;
import org.project_kessel.api.auth.OAuth2Exception;
import org.project_kessel.api.rbac.v2.FetchWorkspace;
import org.project_kessel.api.rbac.v2.Workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@ApplicationScoped
public class WorkspaceUtils {

    @ConfigProperty(name = "rbac.workspace-lookup.enabled", defaultValue = "true")
    boolean workspaceLookupEnabled;

    @Inject
    OAuth2ClientCredentialsCache oauth2ClientCredentialsCache;

    @Inject
    BackendConfig backendConfig;

    /**
     * Returns the identifier of the default workspace for the given
     * organization. The result is cached because the identifier is not going
     * to change as long as we are fetching this data from RBAC.
     *
     * When workspace lookup is disabled (rbac.workspace-lookup.enabled=false),
     * returns a deterministic UUID generated from the org_id. This is useful
     * for local development and testing without requiring RBAC service access.
     *
     * RBAC's default workspace endpoint can return null or fail for legacy orgs
     * (particularly on stage). In such cases, this method returns null to let
     * callers decide how to handle missing workspace data.
     *
     * @param orgId the organization to get the default workspace from.
     * @return the identifier of the workspace, or null if RBAC returns no workspace.
     */
    @CacheResult(cacheName = "kessel-rbac-workspace-id")
    @Retry(maxRetries = 3, delay = 100, retryOn = OAuth2Exception.class)
    public UUID getDefaultWorkspaceId(final String orgId) {

        if (!workspaceLookupEnabled) {
            // When workspace lookup is disabled, generate a deterministic UUID for testing/development
            UUID workspaceId = UUID.nameUUIDFromBytes(
                ("workspace-for-" + orgId).getBytes(StandardCharsets.UTF_8)
            );
            Log.debugf("[WORKSPACE LOOKUP DISABLED][org_id: %s][workspace_id: %s] Using deterministic workspace ID", orgId, workspaceId);
            return workspaceId;
        }

        OAuth2ClientCredentials credentials;
        try {
            credentials = oauth2ClientCredentialsCache.getCredentials();
        } catch (OAuth2Exception e) {
            Log.warnf("Transient error fetching OAuth2 credentials for RBAC workspace lookup (may retry): %s", e.getMessage());
            throw e;
        }
        OAuth2AuthRequest authRequest = new OAuth2AuthRequest(credentials);

        Workspace workspace;
        try {
            workspace = FetchWorkspace.fetchDefaultWorkspace(backendConfig.getRbacUrl(), orgId, authRequest);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to fetch a default workspace from RBAC", e);
        }

        // RBAC's default workspace endpoint can return null or fail for legacy orgs
        // (particularly on stage). Null handling is intentional defensive coding to
        // allow callers to decide how to handle missing workspace data.
        if (workspace == null || workspace.getId() == null) {
            Log.warnf("[org_id: %s] RBAC returned null or empty workspace", orgId);
            return null;
        }

        Log.debugf("[org_id: %s][workspace_id: %s] Fetched default workspace from RBAC", orgId, workspace.getId());
        return UUID.fromString(workspace.getId());
    }
}
