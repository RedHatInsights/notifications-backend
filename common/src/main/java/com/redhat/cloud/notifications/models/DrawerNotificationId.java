package com.redhat.cloud.notifications.models;

import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.Objects;


@Embeddable
public class DrawerNotificationId implements Serializable {

    @NotNull
    @Size(max = 50)
    private String orgId;

    @NotNull
    @Size(max = 50)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    public DrawerNotificationId() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DrawerNotificationId that = (DrawerNotificationId) o;
        return orgId.equals(that.orgId) && userId.equals(that.userId) && event.equals(that.event);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, userId, event);
    }
}
