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
@Table(name = "event_type_behavior")
@JsonNaming(SnakeCaseStrategy.class)
public class EventTypeBehavior extends CreationTimestamped {

    @EmbeddedId
    private EventTypeBehaviorId id;

    @ManyToOne
    @MapsId("eventTypeId")
    @JoinColumn(name = "event_type_id")
    private EventType eventType;

    @ManyToOne
    @MapsId("behaviorGroupId")
    @JoinColumn(name = "behavior_group_id")
    @JsonIgnore
    private BehaviorGroup behaviorGroup;

    public EventTypeBehavior() {
    }

    public EventTypeBehavior(EventType eventType, BehaviorGroup behaviorGroup) {
        id = new EventTypeBehaviorId(); // Required to prevent a Hibernate NPE at persistence time.
        this.eventType = eventType;
        this.behaviorGroup = behaviorGroup;
    }

    public EventTypeBehaviorId getId() {
        return id;
    }

    public void setId(EventTypeBehaviorId id) {
        this.id = id;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public BehaviorGroup getBehaviorGroup() {
        return behaviorGroup;
    }

    public void setBehaviorGroup(BehaviorGroup behaviorGroup) {
        this.behaviorGroup = behaviorGroup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EventTypeBehavior) {
            EventTypeBehavior other = (EventTypeBehavior) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
