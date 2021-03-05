package com.redhat.cloud.notifications.db.entities;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class EndpointDefaultEntityId implements Serializable {

    @Column(name = "endpoint_id")
    @NotNull
    public UUID endpointId;

    @Column(name = "account_id")
    @NotNull
    public String accountId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EndpointDefaultEntityId) {
            EndpointDefaultEntityId other = (EndpointDefaultEntityId) o;
            return Objects.equals(endpointId, other.endpointId) &&
                    Objects.equals(accountId, other.accountId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpointId, accountId);
    }
}
