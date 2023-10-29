package com.redhat.cloud.notifications.models;

import com.redhat.cloud.notifications.db.converters.SubscriptionTypeConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class EventTypeEmailSubscriptionId implements Serializable {

    @NotNull
    @Size(max = 50)
    public String orgId;

    @NotNull
    @Size(max = 50)
    public String userId;

    @NotNull
    public UUID eventTypeId;

    @NotNull
    @Size(max = 50)
    @Convert(converter = SubscriptionTypeConverter.class)
    public SubscriptionType subscriptionType;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EventTypeEmailSubscriptionId) {
            EventTypeEmailSubscriptionId other = (EventTypeEmailSubscriptionId) o;
            return Objects.equals(userId, other.userId) &&
                Objects.equals(orgId, other.orgId) &&
                Objects.equals(eventTypeId, other.eventTypeId) &&
                Objects.equals(subscriptionType, other.subscriptionType);
        }
        return false;
    }

    public EventTypeEmailSubscriptionId(String orgId, String userId, UUID eventTypeId, SubscriptionType subscriptionType) {
        this.orgId = orgId;
        this.userId = userId;
        this.eventTypeId = eventTypeId;
        this.subscriptionType = subscriptionType;
    }

    public EventTypeEmailSubscriptionId() {
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, userId, eventTypeId, subscriptionType);
    }


}
