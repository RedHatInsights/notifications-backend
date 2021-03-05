package com.redhat.cloud.notifications.db.entities;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "endpoint_defaults")
public class EndpointDefaultEntity {

    @EmbeddedId
    public EndpointDefaultEntityId id;

    @ManyToOne
    @MapsId("endpointId")
    @JoinColumn(name = "endpoint_id")
    public EndpointEntity endpoint;

    public EndpointDefaultEntity() {
    }

    public EndpointDefaultEntity(String accountId, EndpointEntity endpoint) {
        id = new EndpointDefaultEntityId();
        id.accountId = accountId;
        this.endpoint = endpoint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EndpointDefaultEntity) {
            EndpointDefaultEntity other = (EndpointDefaultEntity) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
