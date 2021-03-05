package com.redhat.cloud.notifications.db.entities;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "endpoint_targets")
public class EndpointTargetEntity {

    @EmbeddedId
    public EndpointTargetEntityId id;

    @ManyToOne
    @MapsId("endpointId")
    @JoinColumn(name = "endpoint_id")
    public EndpointEntity endpoint;

    @ManyToOne
    @MapsId("eventTypeId")
    @JoinColumn(name = "event_type_id")
    public EventTypeEntity eventType;

    public EndpointTargetEntity() {
    }

    public EndpointTargetEntity(String accountId, EndpointEntity endpoint, EventTypeEntity eventType) {
        id = new EndpointTargetEntityId();
        id.accountId = accountId;
        this.endpoint = endpoint;
        this.eventType = eventType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EndpointTargetEntity) {
            EndpointTargetEntity other = (EndpointTargetEntity) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
