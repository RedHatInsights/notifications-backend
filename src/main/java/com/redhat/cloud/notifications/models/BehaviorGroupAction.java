package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;

import java.util.Objects;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@Entity
@Table(name = "behavior_group_action")
@JsonNaming(SnakeCaseStrategy.class)
public class BehaviorGroupAction extends CreationTimestamped {

    @EmbeddedId
    private BehaviorGroupActionId id;

    @ManyToOne
    @MapsId("behaviorGroupId")
    @JoinColumn(name = "behavior_group_id")
    private BehaviorGroup behaviorGroup;

    @ManyToOne
    @MapsId("endpointId")
    @JoinColumn(name = "endpoint_id")
    private Endpoint endpoint;

    public BehaviorGroupAction() {
    }

    public BehaviorGroupAction(BehaviorGroup behaviorGroup, Endpoint endpoint) {
        id = new BehaviorGroupActionId(); // Required to prevent a Hibernate NPE at persistence time.
        this.behaviorGroup = behaviorGroup;
        this.endpoint = endpoint;
    }

    public BehaviorGroupActionId getId() {
        return id;
    }

    public void setId(BehaviorGroupActionId id) {
        this.id = id;
    }

    public BehaviorGroup getBehaviorGroup() {
        return behaviorGroup;
    }

    public void setBehaviorGroup(BehaviorGroup behaviorGroup) {
        this.behaviorGroup = behaviorGroup;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof BehaviorGroupAction) {
            BehaviorGroupAction other = (BehaviorGroupAction) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
