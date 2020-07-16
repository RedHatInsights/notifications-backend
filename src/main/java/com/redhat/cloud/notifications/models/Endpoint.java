package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "endpoints")
public class Endpoint {

//}<T extends Attributes> {
    public enum EndpointType {
        WEBHOOK, EMAIL;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id; // Should be UUID

    @JsonIgnore
    @Column(name = "account_id")
    private String tenant;

    private String name;
    private String description;
    private boolean enabled;

    @Column(name = "endpoint_type")
    private EndpointType type;

//    @CreationTimestamp
//    @ColumnDefault("CURRENT_TIMESTAMP")
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created", updatable = false)
    private Date created;

//    @UpdateTimestamp
//    @ColumnDefault("CURRENT_TIMESTAMP")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updated;

    @Transient // Ignore for Entity usage
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

    @PrePersist
    public void prePersist() {
        created = Date.from(Instant.now());
    }

    @PreUpdate
    public void preUpdate() {
        updated = Date.from(Instant.now());
    }
}
