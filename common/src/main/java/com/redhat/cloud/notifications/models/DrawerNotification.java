package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;


@Entity
@Table(name = "drawer_notification")
@JsonNaming(SnakeCaseStrategy.class)
public class DrawerNotification extends CreationTimestamped {

    public static final Map<String, String> SORT_FIELDS = Map.of(
        "bundle", "dn.event.bundleDisplayName",
        "application", "dn.event.applicationDisplayName",
        "event", "dn.event.eventTypeDisplayName",
        "created", "dn.created",
        "read", "dn.read"
    );

    @EmbeddedId
    private DrawerNotificationId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("eventId")
    @JoinColumn(name = "event_id")
    private Event event;

    @NotNull
    private boolean read;

    public DrawerNotification() {
    }

    public DrawerNotification(String orgId, String userId, Event event) {
        id = new DrawerNotificationId();
        id.setUserId(userId);
        id.setOrgId(orgId);
        setEvent(event);
    }

    public String getUserId() {
        return id.getUserId();
    }

    public void setUserId(String userId) {
        id.setUserId(userId);
    }

    public String getOrgId() {
        return id.getOrgId();
    }

    public void setOrgId(String orgId) {
        id.setOrgId(orgId);
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

}
