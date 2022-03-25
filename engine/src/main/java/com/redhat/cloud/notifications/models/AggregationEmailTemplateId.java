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
public class AggregationEmailTemplateId implements Serializable {

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
        if (o instanceof AggregationEmailTemplateId) {
            AggregationEmailTemplateId other = (AggregationEmailTemplateId) o;
            return Objects.equals(applicationId, other.applicationId) &&
                    Objects.equals(subscriptionType, other.subscriptionType);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(applicationId, subscriptionType);
    }
}
