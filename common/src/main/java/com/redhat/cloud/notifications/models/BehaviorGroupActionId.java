package com.redhat.cloud.notifications.models;

import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class BehaviorGroupActionId implements Serializable {

    @NotNull
    public UUID behaviorGroupId;

    @NotNull
    public UUID endpointId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof BehaviorGroupActionId) {
            BehaviorGroupActionId other = (BehaviorGroupActionId) o;
            return Objects.equals(behaviorGroupId, other.behaviorGroupId) &&
                    Objects.equals(endpointId, other.endpointId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(behaviorGroupId, endpointId);
    }
}
