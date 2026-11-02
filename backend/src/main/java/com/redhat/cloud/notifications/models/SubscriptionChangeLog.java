package com.redhat.cloud.notifications.models;

import com.redhat.cloud.notifications.db.converters.SubscriptionTypeConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "subscription_change_log")
public class SubscriptionChangeLog extends CreationTimestamped {

    @Id
    @GeneratedValue
    private UUID id;

    private String orgId;

    private String userId;

    private UUID eventTypeId;

    @Convert(converter = SubscriptionTypeConverter.class)
    private SubscriptionType subscriptionType;

    private boolean subscribed;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public UUID getEventTypeId() {
        return eventTypeId;
    }

    public void setEventTypeId(UUID eventTypeId) {
        this.eventTypeId = eventTypeId;
    }

    public SubscriptionType getSubscriptionType() {
        return subscriptionType;
    }

    public void setSubscriptionType(SubscriptionType subscriptionType) {
        this.subscriptionType = subscriptionType;
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
        if (o instanceof SubscriptionChangeLog other) {
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
