package com.redhat.cloud.notifications.qute.templates;

public enum IntegrationType {
    DRAWER("drawer"),
    EMAIL_BODY("email"),
    EMAIL_TITLE("email"),
    EMAIL_DAILY_DIGEST_BODY("email"),
    SLACK("slack"),
    MS_TEAMS("ms_teams"),
    GOOGLE_CHAT("google_chat");

    private final String rootFolder;

    IntegrationType(final String rootFolder) {
        this.rootFolder = rootFolder;
    }

    public String getRootFolder() {
        return rootFolder;
    }
}
