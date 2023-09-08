package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

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
    @JsonIgnore
    private BehaviorGroup behaviorGroup;

    @ManyToOne
    @MapsId("endpointId")
    @JoinColumn(name = "endpoint_id")
    private Endpoint endpoint;

    // The position determines the display order of a behavior group actions in the UI.
    @JsonIgnore
    private int position;

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

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
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
