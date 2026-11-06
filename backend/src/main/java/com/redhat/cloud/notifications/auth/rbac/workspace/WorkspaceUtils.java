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

    @Inject
    WorkspaceUtils self;

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
     * callers decide how to handle missing workspace data. Null results are not
     * cached so that subsequent lookups can succeed if a workspace is provisioned.
     *
     * @param orgId the organization to get the default workspace from.
     * @return the identifier of the workspace, or null if RBAC returns no workspace.
     */
    public UUID getDefaultWorkspaceId(final String orgId) {

        if (!workspaceLookupEnabled) {
            // When workspace lookup is disabled, generate a deterministic UUID for testing/development
            UUID workspaceId = UUID.nameUUIDFromBytes(
                ("workspace-for-" + orgId).getBytes(StandardCharsets.UTF_8)
            );
            Log.debugf("[WORKSPACE LOOKUP DISABLED][org_id: %s][workspace_id: %s] Using deterministic workspace ID", orgId, workspaceId);
            return workspaceId;
        }

        try {
            WorkspaceUtils target = self != null ? self : this;
            return target.fetchDefaultWorkspaceId(orgId);
        } catch (WorkspaceNotFoundException e) {
            return null;
        }
    }

    /**
     * Fetches and caches the default workspace ID from RBAC.
     * Throws {@link WorkspaceNotFoundException} when RBAC returns null or empty workspace data,
     * which prevents caching of missing workspaces.
     *
     * @param orgId the organization to get the default workspace from.
     * @return the identifier of the workspace.
     */
    @CacheResult(cacheName = "kessel-rbac-workspace-id")
    @Retry(maxRetries = 3, delay = 100, retryOn = OAuth2Exception.class)
    public UUID fetchDefaultWorkspaceId(final String orgId) {

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
            workspace = fetchWorkspace(backendConfig.getRbacUrl(), orgId, authRequest);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to fetch a default workspace from RBAC", e);
        }

        // RBAC's default workspace endpoint can return null or fail for legacy orgs
        // (particularly on stage). Null handling is intentional defensive coding to
        // allow callers to decide how to handle missing workspace data. Throwing an
        // exception prevents caching null results in the Quarkus cache.
        if (workspace == null || workspace.getId() == null || workspace.getId().isEmpty()) {
            Log.warnf("[org_id: %s] RBAC returned null or empty workspace", orgId);
            throw new WorkspaceNotFoundException();
        }

        Log.debugf("[org_id: %s][workspace_id: %s] Fetched default workspace from RBAC", orgId, workspace.getId());
        return UUID.fromString(workspace.getId());
    }

    Workspace fetchWorkspace(final String rbacUrl, final String orgId, final OAuth2AuthRequest authRequest) throws IOException, InterruptedException {
        return FetchWorkspace.fetchDefaultWorkspace(rbacUrl, orgId, authRequest);
    }

    static class WorkspaceNotFoundException extends RuntimeException {
    }
}
