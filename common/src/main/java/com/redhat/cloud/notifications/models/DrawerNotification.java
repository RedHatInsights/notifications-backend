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
        "read", "dn.read",
        "inventory_url", "dn.inventoryUrl",
        "application_url", "dn.applicationUrl"
    );

    @EmbeddedId
    private DrawerNotificationId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("eventId")
    @JoinColumn(name = "event_id")
    private Event event;

    @NotNull
    private boolean read;

    @NotNull
    private String inventoryUrl;

    @NotNull
    private String applicationUrl;

    public DrawerNotification() {
    }

    public DrawerNotification(String orgId, String userId, Event event) {
        this(orgId, userId, event, "", "");
    }

    public DrawerNotification(String orgId, String userId, Event event, String inventoryUrl, String applicationUrl) {
        id = new DrawerNotificationId();
        id.setUserId(userId);
        id.setOrgId(orgId);
        setEvent(event);
        this.inventoryUrl = inventoryUrl;
        this.applicationUrl = applicationUrl;
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

    public String getInventoryUrl() {
        return inventoryUrl;
    }

    public void setInventoryUrl(String inventoryUrl) {
        this.inventoryUrl = inventoryUrl;
    }

    public String getApplicationUrl() {
        return applicationUrl;
    }

    public void setApplicationUrl(String applicationUrl) {
        this.applicationUrl = applicationUrl;
    }
}
