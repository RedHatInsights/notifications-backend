package com.redhat.cloud.notifications.routers.internal.models;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.constraints.NotNull;
import java.util.Objects;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RequestDefaultBehaviorGroupPropertyList {
    @NotNull
    private boolean onlyAdmins;

    @NotNull
    private boolean ignorePreferences;

    public boolean isOnlyAdmins() {
        return onlyAdmins;
    }

    public void setOnlyAdmins(boolean onlyAdmins) {
        this.onlyAdmins = onlyAdmins;
    }

    public boolean isIgnorePreferences() {
        return ignorePreferences;
    }

    public void setIgnorePreferences(boolean ignorePreferences) {
        this.ignorePreferences = ignorePreferences;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RequestDefaultBehaviorGroupPropertyList that = (RequestDefaultBehaviorGroupPropertyList) o;
        return onlyAdmins == that.onlyAdmins && ignorePreferences == that.ignorePreferences;
    }

    @Override
    public int hashCode() {
        return Objects.hash(onlyAdmins, ignorePreferences);
    }
}
