package com.redhat.cloud.notifications.models.secrets;

/**
 * Definition of the secret types Notifications supports.
 */
public enum SecretType {
    BASIC_AUTHENTICATION("basic authentication"),
    BEARER_TOKEN("bearer token"),
    SECRET_TOKEN("secret token");

    private final String displayName;

    SecretType(final String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the predefined display name for the secret type.
     * @return the display name for the secret type, in a user-friendly
     * version.
     */
    public String getDisplayName() {
        return this.displayName;
    }
}
