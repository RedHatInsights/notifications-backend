package com.redhat.cloud.notifications.models.dto.v1.endpoint;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.properties.CamelPropertiesDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.properties.EndpointPropertiesDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.properties.SystemSubscriptionPropertiesDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.properties.WebhookPropertiesDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class EndpointDTO {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID id;

    @NotNull
    @Size(max = 255)
    private String name;

    @NotNull
    private String description;

    private Boolean enabled = Boolean.FALSE;

    private EndpointStatusDTO status = EndpointStatusDTO.UNKNOWN;

    @Min(0)
    private int serverErrors;

    @NotNull
    private EndpointTypeDTO type;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Size(max = 20)
    private String subType;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime created;

    @JsonSubTypes({
        @JsonSubTypes.Type(value = CamelPropertiesDTO.class, name = "camel"),
        @JsonSubTypes.Type(value = CamelPropertiesDTO.class, name = "CAMEL"),
        @JsonSubTypes.Type(value = SystemSubscriptionPropertiesDTO.class, name = "drawer"),
        @JsonSubTypes.Type(value = SystemSubscriptionPropertiesDTO.class, name = "DRAWER"),
        @JsonSubTypes.Type(value = SystemSubscriptionPropertiesDTO.class, name = "email_subscription"),
        @JsonSubTypes.Type(value = SystemSubscriptionPropertiesDTO.class, name = "EMAIL_SUBSCRIPTION"),
        @JsonSubTypes.Type(value = WebhookPropertiesDTO.class, name = "ansible"),
        @JsonSubTypes.Type(value = WebhookPropertiesDTO.class, name = "ANSIBLE"),
        @JsonSubTypes.Type(value = WebhookPropertiesDTO.class, name = "webhook"),
        @JsonSubTypes.Type(value = WebhookPropertiesDTO.class, name = "WEBHOOK")
    })
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type",
        include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
    @Schema(oneOf = { CamelPropertiesDTO.class, SystemSubscriptionPropertiesDTO.class, WebhookPropertiesDTO.class })
    @Valid
    private EndpointPropertiesDTO properties;

    @JsonIgnore
    @AssertTrue(message = "This type requires a sub_type")
    private boolean isSubTypePresentWhenRequired() {
        return !this.type.requiresSubType || this.subType != null;
    }

    @JsonIgnore
    @AssertTrue(message = "This type does not support sub_type")
    private boolean isSubTypeNotPresentWhenNotRequired() {
        return this.type.requiresSubType || this.subType == null;
    }

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(final Boolean enabled) {
        this.enabled = enabled;
    }

    public EndpointStatusDTO getStatus() {
        return status;
    }

    public void setStatus(final EndpointStatusDTO status) {
        this.status = status;
    }

    public int getServerErrors() {
        return serverErrors;
    }

    public void setServerErrors(final int serverErrors) {
        this.serverErrors = serverErrors;
    }

    public EndpointTypeDTO getType() {
        return type;
    }

    public void setType(final EndpointTypeDTO type) {
        this.type = type;
    }

    public String getSubType() {
        return subType;
    }

    public void setSubType(final String subType) {
        this.subType = subType;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(final LocalDateTime created) {
        this.created = created;
    }

    public EndpointPropertiesDTO getProperties() {
        return properties;
    }

    public void setProperties(final EndpointPropertiesDTO properties) {
        this.properties = properties;
    }
}
