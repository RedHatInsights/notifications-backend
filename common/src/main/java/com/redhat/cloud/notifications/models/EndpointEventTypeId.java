package com.redhat.cloud.notifications.models;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class EndpointEventTypeId implements Serializable {

    @NotNull
    public UUID eventTypeId;

    @NotNull
    public UUID endpointId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EndpointEventTypeId that = (EndpointEventTypeId) o;
        return Objects.equals(eventTypeId, that.eventTypeId) && Objects.equals(endpointId, that.endpointId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventTypeId, endpointId);
    }
}
