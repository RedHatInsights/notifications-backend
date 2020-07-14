package com.redhat.cloud.notifications.models;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

@Entity
@Table(name = "endpoints")
public class Endpoint <T extends Attributes> {
    enum EndpointType {
        WEBHOOK, EMAIL;
    }

    @Id
    private String id; // Should be UUID

    @Column(name = "account_id")
    private String tenant;

    private String name;
    private String description;
    private boolean enabled;
    private EndpointType type;
    private long created;

    @Transient
    private T properties;

    public Endpoint() {
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public EndpointType getType() {
        return type;
    }

    public long getCreated() {
        return created;
    }

    public T getProperties() {
        return properties;
    }

    public void setProperties(T properties) {
        this.properties = properties;
    }
}
