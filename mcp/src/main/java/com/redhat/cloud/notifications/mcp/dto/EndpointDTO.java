package com.redhat.cloud.notifications.mcp.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * Integration endpoint configuration for MCP requests.
 *
 * Based on: backend/src/main/java/com/redhat/cloud/notifications/models/dto/v1/endpoint/EndpointDTO.java
 *
 * Mirrors the backend EndpointDTO structure for JSON compatibility with polymorphic
 * properties deserialization via @JsonSubTypes. The 'type' field acts as an external
 * discriminator that determines which properties DTO subclass to deserialize.
 *
 * Supported types (see EndpointTypeDTO enum):
 * - WEBHOOK: General HTTP webhooks (uses WebhookPropertiesDTO)
 * - ANSIBLE: Ansible Tower webhooks (uses WebhookPropertiesDTO)
 * - CAMEL: Various camel connectors - requires subType (uses CamelPropertiesDTO)
 *   - subType values: slack, teams, google_chat, servicenow, splunk
 * - PAGERDUTY: PagerDuty integration (uses PagerDutyPropertiesDTO)
 * - DRAWER: Drawer notifications (uses SystemSubscriptionPropertiesDTO, system type)
 * - EMAIL_SUBSCRIPTION: Email subscriptions (uses SystemSubscriptionPropertiesDTO, system type)
 *
 * Intentional differences from backend DTO (request vs response):
 * - No 'id' field: For CREATE, server generates it. For UPDATE, ID is passed as URL path parameter,
 *   not in request body (see BackendRestClient.updateEndpoint).
 * - No read-only fields: 'status', 'serverErrors', 'created', 'updated', and
 *   'eventTypesGroupByBundlesAndApplications' are response-only fields managed by the backend.
 *   These are marked @JsonProperty(access = READ_ONLY) in backend and ignored on input.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EndpointDTO {

    @NotNull
    @Size(max = 255)
    private String name;

    @NotNull
    private String description;

    private Boolean enabled = Boolean.FALSE;

    @NotNull
    private EndpointTypeDTO type;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private CamelSubType subType;

    @JsonSubTypes({
        @JsonSubTypes.Type(value = CamelPropertiesDTO.class, name = "camel"),
        @JsonSubTypes.Type(value = SystemSubscriptionPropertiesDTO.class, name = "drawer"),
        @JsonSubTypes.Type(value = SystemSubscriptionPropertiesDTO.class, name = "email_subscription"),
        @JsonSubTypes.Type(value = WebhookPropertiesDTO.class, name = "ansible"),
        @JsonSubTypes.Type(value = WebhookPropertiesDTO.class, name = "webhook"),
        @JsonSubTypes.Type(value = PagerDutyPropertiesDTO.class, name = "pagerduty"),
    })
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type",
        include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
    @Valid
    @NotNull
    private EndpointPropertiesDTO properties;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Set<UUID> eventTypes;

    @JsonIgnore
    @AssertTrue(message = "This type requires a sub_type")
    private boolean isSubTypePresentWhenRequired() {
        return type == null || !type.requiresSubType || subType != null;
    }

    @JsonIgnore
    @AssertTrue(message = "This type does not support sub_type")
    private boolean isSubTypeNotPresentWhenNotRequired() {
        return type == null || type.requiresSubType || subType == null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public EndpointTypeDTO getType() {
        return type;
    }

    public void setType(EndpointTypeDTO type) {
        this.type = type;
    }

    public CamelSubType getSubType() {
        return subType;
    }

    public void setSubType(CamelSubType subType) {
        this.subType = subType;
    }

    public EndpointPropertiesDTO getProperties() {
        return properties;
    }

    public void setProperties(EndpointPropertiesDTO properties) {
        this.properties = properties;
    }

    public Set<UUID> getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(Set<UUID> eventTypes) {
        this.eventTypes = eventTypes;
    }
}
