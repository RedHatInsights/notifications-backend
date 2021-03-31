package com.redhat.cloud.notifications.models;

import com.redhat.cloud.notifications.db.converters.EmailSubscriptionTypeConverter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class EmailSubscriptionId implements Serializable {

    @NotNull
    @Size(max = 50)
    public String accountId;

    @NotNull
    @Size(max = 50)
    public String userId;

    @Column(name = "bundle")
    @NotNull
    @Size(max = 255)
    public String bundleName;

    @Column(name = "application")
    @NotNull
    @Size(max = 255)
    public String applicationName;

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
                    Objects.equals(bundleName, other.bundleName) &&
                    Objects.equals(applicationName, other.applicationName);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, userId, subscriptionType, applicationName, bundleName);
    }
}
