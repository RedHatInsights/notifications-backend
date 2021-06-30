package com.redhat.cloud.notifications.models;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;
import java.util.Objects;

// TODO [BG Phase 2] Delete this class
@Entity
@Table(name = "endpoint_targets")
public class EndpointTarget {

    @EmbeddedId
    private EndpointTargetId id;

    @ManyToOne
    @MapsId("endpointId")
    @JoinColumn(name = "endpoint_id")
    private Endpoint endpoint;

    @ManyToOne
    @MapsId("eventTypeId")
    @JoinColumn(name = "event_type_id")
    private EventType eventType;

    public EndpointTarget() {
    }

    public EndpointTarget(String accountId, Endpoint endpoint, EventType eventType) {
        id = new EndpointTargetId();
        id.accountId = accountId;
        this.endpoint = endpoint;
        this.eventType = eventType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EndpointTarget) {
            EndpointTarget other = (EndpointTarget) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
