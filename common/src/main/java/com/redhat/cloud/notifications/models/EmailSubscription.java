package com.redhat.cloud.notifications.models;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import java.util.Objects;

@Entity
@Table(name = "endpoint_email_subscriptions")
public class EmailSubscription {

    @EmbeddedId
    private EmailSubscriptionId id;

    @Size(max = 50)
    private String accountId;

    @ManyToOne
    @MapsId("applicationId")
    @JoinColumn(name = "application_id")
    private Application application;

    public void setId(EmailSubscriptionId id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getOrgId() {
        return id.orgId;
    }

    public void setOrgId(String orgId) {
        id.orgId = orgId;
    }

    public String getUserId() {
        return id.userId;
    }

    public void setUserId(String userId) {
        id.userId = userId;
    }

    public EmailSubscriptionType getType() {
        return id.subscriptionType;
    }

    public void setType(EmailSubscriptionType type) {
        id.subscriptionType = type;
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
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
