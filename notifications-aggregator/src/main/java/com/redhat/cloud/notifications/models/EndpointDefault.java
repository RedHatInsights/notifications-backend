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
@Table(name = "endpoint_defaults")
public class EndpointDefault {

    @EmbeddedId
    private EndpointDefaultId id;

    @ManyToOne
    @MapsId("endpointId")
    @JoinColumn(name = "endpoint_id")
    private Endpoint endpoint;

    public EndpointDefault() {
    }

    public EndpointDefault(String accountId, Endpoint endpoint) {
        id = new EndpointDefaultId();
        id.accountId = accountId;
        this.endpoint = endpoint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EndpointDefault) {
            EndpointDefault other = (EndpointDefault) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
