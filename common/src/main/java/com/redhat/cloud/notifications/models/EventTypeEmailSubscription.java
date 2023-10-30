package com.redhat.cloud.notifications.models;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@Entity
@Table(name = "email_subscriptions")
public class EventTypeEmailSubscription {

    @EmbeddedId
    private EventTypeEmailSubscriptionId id;

    @ManyToOne
    @MapsId("eventTypeId")
    @JoinColumn(name = "event_type_id")
    private EventType eventType;

    @NotNull
    private boolean subscribed;

    public void setId(EventTypeEmailSubscriptionId id) {
        this.id = id;
    }

    public String getOrgId() {
        return id.orgId;
    }

    public void setOrgId(String orgId) {
        id.orgId = orgId;
    }

    public String getUserId() {
        return id.userId;
    }

    public void setUserId(String userId) {
        id.userId = userId;
    }

    public SubscriptionType getType() {
        return id.subscriptionType;
    }

    public void setType(SubscriptionType type) {
        id.subscriptionType = type;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public boolean isSubscribed() {
        return subscribed;
    }

    public void setSubscribed(boolean subscribed) {
        this.subscribed = subscribed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EventTypeEmailSubscription) {
            EventTypeEmailSubscription other = (EventTypeEmailSubscription) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
