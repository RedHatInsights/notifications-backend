package com.redhat.cloud.notifications.routers.internal.models;

import java.util.HashSet;
import java.util.Set;

public class InternalUserPermissions {
    private boolean isAdmin;
    private final Set<String> applicationIds = new HashSet<>();

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

    public Set<String> getApplicationIds() {
        return applicationIds;
    }

    public void addApplicationId(String applicationId) {
        this.applicationIds.add(applicationId);
    }
}
