package com.redhat.cloud.notifications.models.subscription;

import com.redhat.cloud.notifications.EmailSubscriptionType;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "endpoint_email_subscriptions")
public class EmailSubscription {

    @EmbeddedId
    private EmailSubscriptionId id;

    @ManyToOne
    @MapsId("applicationId")
    @JoinColumn(name = "application_id")
    private Application application;

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
