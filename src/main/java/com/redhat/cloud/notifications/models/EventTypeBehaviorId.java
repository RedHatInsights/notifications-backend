package com.redhat.cloud.notifications.models;

import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class EventTypeBehaviorId implements Serializable {

    @NotNull
    public UUID eventTypeId;

    @NotNull
    public UUID behaviorGroupId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EventTypeBehaviorId) {
            EventTypeBehaviorId other = (EventTypeBehaviorId) o;
            return Objects.equals(eventTypeId, other.eventTypeId) &&
                    Objects.equals(behaviorGroupId, other.behaviorGroupId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventTypeId, behaviorGroupId);
    }
}
