package com.redhat.cloud.notifications.routers.models;

import javax.validation.constraints.NotNull;

public class RequestEmailSubscriptionProperties {
    @NotNull
    private boolean onlyAdmins;

    public boolean isOnlyAdmins() {
        return onlyAdmins;
    }

    public void setOnlyAdmins(boolean onlyAdmins) {
        this.onlyAdmins = onlyAdmins;
    }
}
