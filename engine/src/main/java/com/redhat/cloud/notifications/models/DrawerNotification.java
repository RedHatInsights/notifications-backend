package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY;
import static java.time.ZoneOffset.UTC;

@Entity
@Table(name = "drawer_notification")
public class DrawerNotification {


    @Id
    @GeneratedValue
    @JsonProperty(access = READ_ONLY)
    private UUID id;

    private Timestamp created;

    @NotNull
    @Size(max = 50)
    private String orgId;

    @NotNull
    @Size(max = 50)
    public String userId;

    @NotNull
    @Transient
    private UUID eventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @NotNull
    private boolean read;

    public DrawerNotification() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public LocalDateTime getCreated() {
        return created.toLocalDateTime();
    }

    public void setCreated(LocalDateTime created) {
        this.created = Timestamp.valueOf(created);
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    @PrePersist
    public void prePersist() {
        // The 'created' field value can be set in tests.
        if (created == null) {
            created = Timestamp.valueOf(LocalDateTime.now(UTC));
        }
    }
}
