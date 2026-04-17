package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

/**
 * Tracks which events a user has marked as read in their drawer.
 * Unlike the old drawer_notification table, this only stores read status (not all notifications).
 * Presence of a row = event is read. Absence = event is unread.
 */
@Entity
@Table(name = "drawer_read_status")
@JsonNaming(SnakeCaseStrategy.class)
public class DrawerReadStatus {

    @EmbeddedId
    private DrawerReadStatusId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("eventId")
    @JoinColumn(name = "event_id")
    private Event event;

    @NotNull
    @Column(name = "read_at")
    private LocalDateTime readAt;

    public DrawerReadStatus() {
    }

    public DrawerReadStatus(String orgId, String userId, UUID eventId, LocalDateTime readAt) {
        this.id = new DrawerReadStatusId(orgId, userId, eventId);
        this.readAt = readAt;
    }

    public DrawerReadStatusId getId() {
        return id;
    }

    public void setId(DrawerReadStatusId id) {
        this.id = id;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }
}
