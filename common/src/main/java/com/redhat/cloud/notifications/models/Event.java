package com.redhat.cloud.notifications.models;

import com.redhat.cloud.notifications.events.EventWrapper;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static java.time.ZoneOffset.UTC;
import static javax.persistence.CascadeType.REMOVE;
import static javax.persistence.FetchType.LAZY;

@Entity
@Table(name = "event")
public class Event {

    public static final Map<String, String> SORT_FIELDS = Map.of(
            "bundle", "e.bundleDisplayName",
            "application", "e.applicationDisplayName",
            "event", "e.eventTypeDisplayName",
            "created", "e.created"
    );

    @Id
    private UUID id;

    private Timestamp created;

    @Size(max = 50)
    private String accountId;

    @NotNull
    @Size(max = 50)
    private String orgId;

    @NotNull
    private UUID bundleId;

    @NotNull
    private String bundleDisplayName;

    @NotNull
    private UUID applicationId;

    @NotNull
    private String applicationDisplayName;

    @NotNull
    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "event_type_id")
    private EventType eventType;

    @NotNull
    private String eventTypeDisplayName;

    @OneToMany(mappedBy = "event", cascade = REMOVE)
    Set<NotificationHistory> historyEntries;

    private String payload;

    @Transient
    private EventWrapper<?, ?> eventWrapper;

    public Event() { }

    public Event(EventType eventType, String payload, EventWrapper<?, ?> eventWrapper) {
        this(eventWrapper.getAccountId(), eventWrapper.getOrgId(), eventType, eventWrapper.getId());
        this.payload = payload;
        this.eventWrapper = eventWrapper;
    }

    public Event(String accountId, String orgId, EventType eventType, UUID eventId) {
        this.accountId = accountId;
        this.orgId = orgId;
        this.eventType = eventType;
        this.id = eventId;
        bundleId = eventType.getApplication().getBundle().getId();
        bundleDisplayName = eventType.getApplication().getBundle().getDisplayName();
        applicationId = eventType.getApplication().getId();
        applicationDisplayName = eventType.getApplication().getDisplayName();
        eventTypeDisplayName = eventType.getDisplayName();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDateTime getCreated() {
        return created.toLocalDateTime();
    }

    public void setCreated(LocalDateTime created) {
        this.created = Timestamp.valueOf(created);
    }

    @PrePersist
    public void prePersist() {
        // The 'created' field value can be set in tests.
        if (created == null) {
            created = Timestamp.valueOf(LocalDateTime.now(UTC));
        }
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public void setBundleId(UUID bundleId) {
        this.bundleId = bundleId;
    }

    public String getBundleDisplayName() {
        return bundleDisplayName;
    }

    public void setBundleDisplayName(String bundleDisplayName) {
        this.bundleDisplayName = bundleDisplayName;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public String getApplicationDisplayName() {
        return applicationDisplayName;
    }

    public void setApplicationDisplayName(String applicationDisplayName) {
        this.applicationDisplayName = applicationDisplayName;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getEventTypeDisplayName() {
        return eventTypeDisplayName;
    }

    public void setEventTypeDisplayName(String eventTypeDisplayName) {
        this.eventTypeDisplayName = eventTypeDisplayName;
    }

    public Set<NotificationHistory> getHistoryEntries() {
        return historyEntries;
    }

    public void setHistoryEntries(Set<NotificationHistory> historyEntries) {
        this.historyEntries = historyEntries;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public EventWrapper<?, ?> getEventWrapper() {
        return eventWrapper;
    }

    public void setEventWrapper(EventWrapper<?, ?> eventWrapper) {
        this.eventWrapper = eventWrapper;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof Event) {
            Event other = (Event) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
