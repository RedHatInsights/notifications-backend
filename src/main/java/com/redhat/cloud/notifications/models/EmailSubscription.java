package com.redhat.cloud.notifications.models;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "endpoint_email_subscriptions")
public class EmailSubscription {

    @EmbeddedId
    private EmailSubscriptionId id;

    public String getAccountId() {
        return id.accountId;
    }

    public void setAccountId(String accountId) {
        id.accountId = accountId;
    }

    public String getUserId() {
        return id.userId;
    }

    public void setUserId(String userId) {
        id.userId = userId;
    }

    public String getBundleName() {
        return id.bundleName;
    }

    public void setBundleName(String bundleName) {
        id.bundleName = bundleName;
    }

    public String getApplicationName() {
        return id.applicationName;
    }

    public void setApplicationName(String applicationName) {
        id.applicationName = applicationName;
    }

    public EmailSubscriptionType getType() {
        return id.subscriptionType;
    }

    public void setType(EmailSubscriptionType type) {
        id.subscriptionType = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EmailSubscription) {
            EmailSubscription other = (EmailSubscription) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
