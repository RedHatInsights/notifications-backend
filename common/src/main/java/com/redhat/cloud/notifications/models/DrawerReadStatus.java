package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

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

    public DrawerReadStatus() {
    }

    public DrawerReadStatus(String orgId, String userId, UUID eventId) {
        this.id = new DrawerReadStatusId(orgId, userId, eventId);
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
}
