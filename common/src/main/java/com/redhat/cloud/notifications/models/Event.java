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

@Entity
@Table(name = "event")
public class Event extends CreationTimestamped {

    @Id
    @GeneratedValue
    private UUID id;

    @NotNull
    @Size(max = 50)
    private String accountId;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "event_type_id")
    private EventType eventType;

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

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
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
