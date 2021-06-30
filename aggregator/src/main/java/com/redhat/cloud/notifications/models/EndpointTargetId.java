package com.redhat.cloud.notifications.models;

import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

// TODO [BG Phase 2] Delete this class
@Embeddable
public class EndpointTargetId implements Serializable {

    @NotNull
    public UUID endpointId;

    public UUID eventTypeId;

    @NotNull
    @Size(max = 50)
    public String accountId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EndpointTargetId) {
            EndpointTargetId other = (EndpointTargetId) o;
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
