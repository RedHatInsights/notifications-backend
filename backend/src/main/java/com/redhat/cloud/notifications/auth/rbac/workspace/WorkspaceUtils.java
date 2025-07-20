package com.redhat.cloud.notifications.auth.rbac.workspace;

import com.redhat.cloud.notifications.auth.rbac.RbacWorkspacesOidcClient;
import com.redhat.cloud.notifications.auth.rbac.RbacWorkspacesPskClient;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.routers.models.Page;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import io.quarkus.security.UnauthorizedException;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.UUID;

@ApplicationScoped
public class WorkspaceUtils {
    /**
     * The application key that is present in RBAC's PSKs file.
     */
    public static final String APPLICATION_KEY = "notifications";

    /**
     * Specifies the default offset for the workspaces. We don't expect to
     * fetch any pages so it can be a constant "0".
     */
    public static final int REQUEST_DEFAULT_OFFSET = 0;

    /**
     * The default limit for the requests. We are expecting to just fetch the
     * default namespace from RBAC, but we are setting the limit to "2" in case
     * we fetch more than one, and so that we can raise an error about it.
     */
    public static final int REQUEST_DEFAULT_LIMIT = 2;

    /**
     * Holds the pre-shared-key Notifications has to use with RBAC to be
     * authorized to make calls.
     */
    private String notificationsPsk;

    @Inject
    BackendConfig backendConfig;

    @Inject
    @RestClient
    RbacWorkspacesPskClient rbacWorkspacesPskClient;

    @Inject
    @RestClient
    RbacWorkspacesOidcClient rbacWorkspacesOidcClient;

    /**
     * Extracts the PSK we need to use to talk to RBAC.
     */
    @PostConstruct
    public void extractNotificationsPsk() {
        final JsonObject psks = this.backendConfig.getRbacPskSecrets();
        final JsonObject notifications = psks.getJsonObject(APPLICATION_KEY);
        this.notificationsPsk = notifications.getString("secret");
        if (this.notificationsPsk == null) {
            this.notificationsPsk = notifications.getString("alt-secret");
        }
    }

    /**
     * Returns the identifier of the default workspace for the given
     * organization. The result is cached because the identifier is not going
     * to change as long as we are fetching this data from RBAC.
     * @param orgId the organization to get the default workspace from.
     * @return the identifier of the workspace.
     */
    @CacheResult(cacheName = "kessel-rbac-workspace-id")
    public UUID getDefaultWorkspaceId(final String orgId) {
        // Call RBAC.
        Page<RbacWorkspace> workspacePage;
        if (backendConfig.isRbacOidcAuthEnabled()) {
            workspacePage = rbacWorkspacesOidcClient.getWorkspaces(
                orgId,
                WorkspaceType.DEFAULT.toString().toLowerCase(),
                REQUEST_DEFAULT_OFFSET,
                REQUEST_DEFAULT_LIMIT);
        } else {
            workspacePage = rbacWorkspacesPskClient.getWorkspaces(
                this.notificationsPsk,
                APPLICATION_KEY,
                orgId,
                WorkspaceType.DEFAULT.toString().toLowerCase(),
                REQUEST_DEFAULT_OFFSET,
                REQUEST_DEFAULT_LIMIT);
        }

        // We have been told we are only going to have one workspace per
        // organization.
        final Long receivedWorkspaceCount = workspacePage.getMeta().getCount();
        if (receivedWorkspaceCount == null) {
            Log.errorf("[org_id: %s] Unable to get the workspace count from the response", orgId);
            throw new UnauthorizedException();
        } else if (receivedWorkspaceCount == 0) {
            Log.errorf("[org_id: %s] No workspace was received in the response", orgId);
            throw new UnauthorizedException();
        } else if (receivedWorkspaceCount > 1) {
            Log.errorf("[org_id: %s][received_workspace_count: %s] More than one workspace received for the organization: %s", orgId, receivedWorkspaceCount, workspacePage.getData());
            throw new UnauthorizedException();
        }

        final RbacWorkspace rbacWorkspace = workspacePage.getData().getFirst();

        // Double check that the fetched workspace is the default one.
        if (!rbacWorkspace.workspaceType().equals(WorkspaceType.DEFAULT)) {
            Log.errorf("[org_id: %s][workspace_id: %s][workspace_type: %s] The fetched workspace is not a default workspace", orgId, rbacWorkspace.id(), rbacWorkspace.workspaceType());

            throw new UnauthorizedException();
        }

        Log.debugf("[org_id: %s][workspace_id: %s] Fetched default workspace from RBAC", orgId, rbacWorkspace.id());

        return rbacWorkspace.id();
    }
}
