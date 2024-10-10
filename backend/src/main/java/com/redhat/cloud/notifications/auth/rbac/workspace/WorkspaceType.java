package com.redhat.cloud.notifications.auth.rbac.workspace;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum WorkspaceType {
    @JsonProperty("default")
    DEFAULT,
    @JsonProperty("root")
    ROOT,
    @JsonProperty("standard")
    STANDARD
}
