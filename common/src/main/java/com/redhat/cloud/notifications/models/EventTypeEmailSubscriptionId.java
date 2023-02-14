package com.redhat.cloud.notifications.models;

import com.redhat.cloud.notifications.db.converters.EmailSubscriptionTypeConverter;

import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
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
    @Convert(converter = EmailSubscriptionTypeConverter.class)
    public EmailSubscriptionType subscriptionType;

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

    public EventTypeEmailSubscriptionId(String orgId, String userId, UUID eventTypeId, EmailSubscriptionType subscriptionType) {
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
