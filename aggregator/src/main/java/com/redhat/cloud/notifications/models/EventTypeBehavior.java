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
@Table(name = "event_type_behavior")
@JsonNaming(SnakeCaseStrategy.class)
public class EventTypeBehavior extends CreationTimestamped {

    @EmbeddedId
    private EventTypeBehaviorId id;

    @ManyToOne
    @MapsId("eventTypeId")
    @JoinColumn(name = "event_type_id")
    private EventType eventType;

    public EventTypeBehavior() {
    }

    public EventTypeBehavior(EventType eventType) {
        id = new EventTypeBehaviorId(); // Required to prevent a Hibernate NPE at persistence time.
        this.eventType = eventType;
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
