package com.redhat.cloud.notifications.models;

import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

// TODO [BG Phase 3] Delete this class
@Embeddable
public class EndpointDefaultId implements Serializable {

    @NotNull
    public String accountId;

    @NotNull
    public UUID endpointId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EndpointDefaultId) {
            EndpointDefaultId other = (EndpointDefaultId) o;
            return Objects.equals(accountId, other.accountId) &&
                    Objects.equals(endpointId, other.endpointId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, endpointId);
    }
}
