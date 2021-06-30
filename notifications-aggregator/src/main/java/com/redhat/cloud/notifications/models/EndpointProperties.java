package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MappedSuperclass;
import javax.persistence.MapsId;
import javax.persistence.OneToOne;

import java.util.Objects;
import java.util.UUID;

import static javax.persistence.FetchType.LAZY;

@MappedSuperclass
public abstract class EndpointProperties {

    /*
     * Because of the @MapsId annotation on the `endpoint` field, an EndpointProperties instance and its parent Endpoint
     * instance will share the same @Id value. As a consequence, the `id` field doesn't need to be generated.
     */
    @Id
    @JsonIgnore
    private UUID id;

    @MapsId
    @OneToOne(fetch = LAZY)
    @JoinColumn(name = "id")
    @JsonIgnore
    private Endpoint endpoint;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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
        if (o instanceof EndpointProperties) {
            EndpointProperties other = (EndpointProperties) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
