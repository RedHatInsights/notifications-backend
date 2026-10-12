package com.redhat.cloud.notifications.models;

import com.redhat.cloud.notifications.db.converters.SubscriptionTypeConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class DigestTriggerOrgConfigId implements Serializable {

    @NotNull
    @Size(max = 50)
    public String orgId;

    @NotNull
    @Convert(converter = SubscriptionTypeConverter.class)
    public SubscriptionType subscriptionType;

    public DigestTriggerOrgConfigId() {
    }

    public DigestTriggerOrgConfigId(String orgId, SubscriptionType subscriptionType) {
        this.orgId = orgId;
        this.subscriptionType = subscriptionType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof DigestTriggerOrgConfigId other) {
            return Objects.equals(orgId, other.orgId)
                && Objects.equals(subscriptionType, other.subscriptionType);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, subscriptionType);
    }
}
