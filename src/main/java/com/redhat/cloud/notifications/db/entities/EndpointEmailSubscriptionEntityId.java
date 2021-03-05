package com.redhat.cloud.notifications.db.entities;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class EndpointEmailSubscriptionEntityId implements Serializable {

    @Column(name = "account_id")
    @NotNull
    @Size(max = 50)
    public String accountId;

    @Column(name = "user_id")
    @NotNull
    @Size(max = 50)
    public String userId;

    @Column(name = "subscription_type")
    @NotNull
    @Size(max = 50)
    public String subscriptionType;

    @Column(name = "bundle")
    @NotNull
    @Size(max = 255)
    public String bundleName;

    @Column(name = "application")
    @NotNull
    @Size(max = 255)
    public String applicationName;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EndpointEmailSubscriptionEntityId) {
            EndpointEmailSubscriptionEntityId other = (EndpointEmailSubscriptionEntityId) o;
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
