package com.redhat.cloud.notifications.auth.rbac.workspace;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents the Kessel's Workspace entity as defined by RBAC.
 * @param id the identifier of the workspace.
 * @param parentId the identifier of the parent workspace.
 * @param workspaceType the type of the workspace.
 * @param name the name of the workspace.
 * @param description the description of the workspace.
 * @param created the date when the workspace was created.
 * @param modified the date when the workspace was modified.
 */
public record RbacWorkspace(
    @JsonProperty("id") UUID id,
    @JsonProperty("parent_id") UUID parentId,
    @JsonProperty("type") WorkspaceType workspaceType,
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("created") LocalDateTime created,
    @JsonProperty("modified") LocalDateTime modified
) {

}
