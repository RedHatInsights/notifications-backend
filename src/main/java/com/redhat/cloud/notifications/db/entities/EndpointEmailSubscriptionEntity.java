package com.redhat.cloud.notifications.db.entities;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "endpoint_email_subscriptions")
public class EndpointEmailSubscriptionEntity {

    @EmbeddedId
    public EndpointEmailSubscriptionEntityId id;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EndpointEmailSubscriptionEntity) {
            EndpointEmailSubscriptionEntity other = (EndpointEmailSubscriptionEntity) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
