package com.redhat.cloud.notifications.models;

import com.redhat.cloud.notifications.ingress.Action;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static javax.persistence.CascadeType.REMOVE;
import static javax.persistence.FetchType.LAZY;

@Entity
@Table(name = "event")
public class Event extends CreationTimestamped {

    @Id
    @GeneratedValue
    private UUID id;

    @NotNull
    @Size(max = 50)
    private String accountId;

    // TODO NOTIF-491 Make this field not nullable here and in the DB after the data migration.
    private UUID bundleId;

    // TODO NOTIF-491 Make this field not nullable here and in the DB after the data migration.
    // TODO NOTIF-491 Should we update this if the bundle is updated?
    private String bundleDisplayName;

    // TODO NOTIF-491 Make this field not nullable here and in the DB after the data migration.
    private UUID applicationId;

    // TODO NOTIF-491 Make this field not nullable here and in the DB after the data migration.
    // TODO NOTIF-491 Should we update this if the application is updated?
    private String applicationDisplayName;

    @NotNull
    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "event_type_id")
    private EventType eventType;

    // TODO NOTIF-491 Make this field not nullable here and in the DB after the data migration.
    // TODO NOTIF-491 Should we update this if the event type is updated?
    private String eventTypeDisplayName;

    @OneToMany(mappedBy = "event", cascade = REMOVE)
    Set<NotificationHistory> historyEntries;

    private String payload;

    @Transient
    private Action action;

    public Event() { }

    public Event(EventType eventType, String payload, Action action) {
        this.accountId = action.getAccountId();
        this.eventType = eventType;
        this.payload = payload;
        this.action = action;
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

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
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

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
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
