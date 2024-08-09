package com.redhat.cloud.notifications.auth.kessel;

public interface Constants {
    /**
     * A placeholder value for the workspace ID for those checks which require
     * querying Kessel about a workspace permission.
     */
    String WORKSPACE_ID_PLACEHOLDER = "workspace-id";
    /**
     * Represents the key for the "resource_type" tag used in the timer.
     */
    String KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY = "resource_type";
}
