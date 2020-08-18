package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Date;
import java.util.UUID;

public class Endpoint {

    public enum EndpointType {
        WEBHOOK, EMAIL
    }

    // TODO Validation for these properties (to prevent errors when inserting)

    private UUID id; // Should be UUID

    @JsonIgnore
    private String tenant;

    private String name;
    private String description;
    private boolean enabled;

    // Transform to lower case in JSON
    private EndpointType type;

    // TODO JSON should be formatted based on the insights type, so ISO8601
    private Date created;

    // TODO JSON should be formatted based on the insights type, so ISO8601
    private Date updated;

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            property = "type",
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = WebhookAttributes.class, name = "webhook"),
            @JsonSubTypes.Type(value = EmailAttributes.class, name = "email"),
    })
    private Attributes properties;

    public Endpoint() {
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
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

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public EndpointType getType() {
        return type;
    }

    @JsonProperty
    public Date getCreated() {
        return created;
    }

    @JsonProperty
    public Date getUpdated() {
        return updated;
    }

    @JsonIgnore
    public void setCreated(Date created) {
        this.created = created;
    }

    @JsonIgnore
    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    public Attributes getProperties() {
        return properties;
    }

    public void setProperties(Attributes properties) {
        this.properties = properties;
    }

    public void setType(EndpointType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "Endpoint{" +
                "id=" + id +
                ", tenant='" + tenant + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", enabled=" + enabled +
                ", type=" + type +
                ", created=" + created +
                ", updated=" + updated +
                ", properties=" + properties +
                '}';
    }
}
