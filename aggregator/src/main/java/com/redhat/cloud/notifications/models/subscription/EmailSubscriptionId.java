package com.redhat.cloud.notifications.models.subscription;

import com.redhat.cloud.notifications.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.converter.EmailSubscriptionTypeConverter;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class EmailSubscriptionId implements Serializable {

    @NotNull
    @Size(max = 50)
    public String accountId;

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
            return Objects.equals(accountId, other.accountId) &&
                    Objects.equals(userId, other.userId) &&
                    Objects.equals(subscriptionType, other.subscriptionType) &&
                    Objects.equals(applicationId, other.applicationId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, userId, subscriptionType, applicationId);
    }
}
