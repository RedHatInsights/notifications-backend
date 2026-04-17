package com.redhat.cloud.notifications.models;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for DrawerReadStatus.
 * Represents a unique combination of (organization, user, event).
 */
@Embeddable
public class DrawerReadStatusId implements Serializable {

    @NotNull
    @Size(max = 50)
    private String orgId;

    @NotNull
    @Size(max = 50)
    private String userId;

    @NotNull
    private UUID eventId;

    public DrawerReadStatusId() {
    }

    public DrawerReadStatusId(String orgId, String userId, UUID eventId) {
        this.orgId = orgId;
        this.userId = userId;
        this.eventId = eventId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DrawerReadStatusId that = (DrawerReadStatusId) o;
        return orgId.equals(that.orgId) && userId.equals(that.userId) && eventId.equals(that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, userId, eventId);
    }
}
