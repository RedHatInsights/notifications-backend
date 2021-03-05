package com.redhat.cloud.notifications.db.entities;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class EndpointTargetEntityId implements Serializable {

    @Column(name = "endpoint_id")
    @NotNull
    public UUID endpointId;

    @Column(name = "event_type_id")
    public UUID eventTypeId;

    @Column(name = "account_id")
    @NotNull
    @Size(max = 50)
    public String accountId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EndpointTargetEntityId) {
            EndpointTargetEntityId other = (EndpointTargetEntityId) o;
            return Objects.equals(endpointId, other.endpointId) &&
                    Objects.equals(eventTypeId, other.eventTypeId) &&
                    Objects.equals(accountId, other.accountId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpointId, eventTypeId, accountId);
    }
}
