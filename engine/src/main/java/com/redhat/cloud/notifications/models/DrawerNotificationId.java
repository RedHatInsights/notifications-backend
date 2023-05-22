package com.redhat.cloud.notifications.models;

import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class DrawerNotificationId implements Serializable {

    @NotNull
    @Size(max = 50)
    public String userId;

    @NotNull
    public UUID eventId;


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof DrawerNotificationId) {
            DrawerNotificationId other = (DrawerNotificationId) o;
            return Objects.equals(userId, other.userId) &&
                Objects.equals(eventId, other.eventId);
        }
        return false;
    }

    public DrawerNotificationId(String userId, UUID eventId) {
        this.userId = userId;
        this.eventId = eventId;
    }

    public DrawerNotificationId() {
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, eventId);
    }

}
