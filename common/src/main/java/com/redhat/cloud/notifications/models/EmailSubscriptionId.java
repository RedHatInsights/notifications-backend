package com.redhat.cloud.notifications.models;

import com.redhat.cloud.notifications.db.converters.EmailSubscriptionTypeConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class EmailSubscriptionId implements Serializable {

    @NotNull
    @Size(max = 50)
    public String orgId;

    @NotNull
    @Size(max = 50)
    public String userId;

    @NotNull
    public UUID applicationId;

    @NotNull
    @Size(max = 50)
    @Convert(converter = EmailSubscriptionTypeConverter.class)
    public EmailSubscriptionType subscriptionType;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EmailSubscriptionId) {
            EmailSubscriptionId other = (EmailSubscriptionId) o;
            return Objects.equals(orgId, other.orgId) &&
                    Objects.equals(userId, other.userId) &&
                    Objects.equals(subscriptionType, other.subscriptionType) &&
                    Objects.equals(applicationId, other.applicationId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, userId, subscriptionType, applicationId);
    }
}
