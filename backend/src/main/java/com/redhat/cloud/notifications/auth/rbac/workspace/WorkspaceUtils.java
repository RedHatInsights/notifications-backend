package com.redhat.cloud.notifications.auth.rbac.workspace;

import com.redhat.cloud.notifications.config.BackendConfig;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.project_kessel.api.auth.ClientConfigAuth;
import org.project_kessel.api.auth.OAuth2AuthRequest;
import org.project_kessel.api.auth.OAuth2ClientCredentials;
import org.project_kessel.api.auth.OIDCDiscovery;
import org.project_kessel.api.auth.OIDCDiscoveryMetadata;
import org.project_kessel.api.rbac.v2.FetchWorkspace;
import org.project_kessel.api.rbac.v2.Workspace;

import java.io.IOException;
import java.util.UUID;

@ApplicationScoped
public class WorkspaceUtils {

    @Inject
    BackendConfig backendConfig;

    /**
     * Returns the identifier of the default workspace for the given
     * organization. The result is cached because the identifier is not going
     * to change as long as we are fetching this data from RBAC.
     * @param orgId the organization to get the default workspace from.
     * @return the identifier of the workspace.
     */
    @CacheResult(cacheName = "kessel-rbac-workspace-id")
    public UUID getDefaultWorkspaceId(final String orgId) {

        OIDCDiscoveryMetadata oidcDiscovery = OIDCDiscovery.fetchOIDCDiscovery(backendConfig.getOidcIssuer());
        ClientConfigAuth authConfig = new ClientConfigAuth(backendConfig.getOidcClientId(), backendConfig.getOidcSecret(), oidcDiscovery.tokenEndpoint());
        OAuth2ClientCredentials credentials = new OAuth2ClientCredentials(authConfig);
        OAuth2AuthRequest authRequest = new OAuth2AuthRequest(credentials);

        Workspace workspace;
        try {
            workspace = FetchWorkspace.fetchDefaultWorkspace(backendConfig.getRbacUrl(), orgId, authRequest);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to fetch a default workspace from RBAC", e);
        }

        Log.debugf("[org_id: %s][workspace_id: %s] Fetched default workspace from RBAC", orgId, workspace.getId());
        return UUID.fromString(workspace.getId());
    }
}
