package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.redhat.cloud.notifications.models.endpoint.attributes.Attributes;
import com.redhat.cloud.notifications.models.endpoint.attributes.EmailSubscriptionAttributes;
import com.redhat.cloud.notifications.models.endpoint.attributes.WebhookAttributes;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.UUID;

public class Endpoint {

    // Add new values to the bottom of the enum
    // The ordinal order must remain the same as is used internally
    // Update test com.redhat.cloud.notifications.models.TestEndpointType to reflect any new enum value
    @Schema(enumeration = { "webhook", "email_subscription", "default" })
    public enum EndpointType {
        @JsonProperty("webhook")
        WEBHOOK, // 0
        @JsonProperty("email_subscription")
        EMAIL_SUBSCRIPTION, // 1
        @JsonProperty("default")
        DEFAULT // 2
    }

    private UUID id; // Should be UUID

    @JsonIgnore
    private String tenant;

    @NotNull
    private String name;
    @NotNull
    private String description;

    private boolean enabled = false;

    // Transform to lower case in JSON
    @NotNull
    private EndpointType type;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Date created;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Date updated;

    @Schema(oneOf = { WebhookAttributes.class, EmailSubscriptionAttributes.class })
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            property = "type",
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = WebhookAttributes.class, name = "webhook"),
            @JsonSubTypes.Type(value = EmailSubscriptionAttributes.class, name = "email_subscription")
    })
//    @NotNull
    @Valid
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

    @JsonGetter
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
