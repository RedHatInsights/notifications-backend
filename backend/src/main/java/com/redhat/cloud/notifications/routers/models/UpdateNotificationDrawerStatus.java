package com.redhat.cloud.notifications.routers.models;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import javax.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UpdateNotificationDrawerStatus {

    @NotNull
    private Set<UUID> notificationIds;

    @NotNull
    private Boolean readStatus;

    public Set<UUID> getNotificationIds() {
        return notificationIds;
    }

    public void setNotificationIds(Set<UUID> notificationIds) {
        this.notificationIds = notificationIds;
    }

    public Boolean getReadStatus() {
        return readStatus;
    }

    public void setReadStatus(Boolean readStatus) {
        this.readStatus = readStatus;
    }
}
