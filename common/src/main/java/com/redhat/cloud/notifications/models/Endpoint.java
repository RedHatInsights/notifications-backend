package com.redhat.cloud.notifications.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "endpoints")
public class Endpoint extends CreationUpdateTimestamped {

    public static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "e.id",
            "name", "e.name",
            "enabled", "e.enabled",
            "type", "e.compositeType.type",
            "created", "e.created"
    );

    // This field is generated (when needed) from the additionalPrePersist method.
    @Id
    private UUID id;

    @Size(max = 50)
    private String accountId;

    @Size(max = 50)
    private String orgId;

    @NotNull
    @Size(max = 255)
    private String name;

    @NotNull
    private String description;

    private Boolean enabled = Boolean.FALSE;

    @Enumerated(EnumType.STRING)
    private EndpointStatus status = EndpointStatus.UNKNOWN;

    @Embedded
    @NotNull
    @Valid
    private final CompositeEndpointType compositeType = new CompositeEndpointType();

    @Min(0)
    private int serverErrors;

    @Transient
    @Valid
    private EndpointProperties properties;

    @OneToMany(mappedBy = "endpoint", cascade = CascadeType.REMOVE)
    private Set<BehaviorGroupAction> behaviorGroupActions;

    private LocalDateTime serverErrorsSince;

    @OneToMany(mappedBy = "endpoint")
    private Set<NotificationHistory> notificationHistories;

    @AssertTrue(message = "This type requires a sub_type")
    private boolean isSubTypePresentWhenRequired() {
        return !compositeType.getType().requiresSubType || compositeType.getSubType() != null;
    }

    @AssertTrue(message = "This type does not support sub_type")
    private boolean isSubTypeNotPresentWhenNotRequired() {
        return compositeType.getType().requiresSubType || compositeType.getSubType() == null;
    }

    @Override
    protected void additionalPrePersist() {
        /*
         * Depending on the endpoint type, the id field may or may not already contain a value when the entity
         * is first persisted.
         */
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
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

    public Boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public EndpointType getType() {
        return compositeType.getType();
    }

    public void setType(EndpointType type) {
        this.compositeType.setType(type);
    }

    public String getSubType() {
        return compositeType.getSubType();
    }

    public void setSubType(String subType) {
        compositeType.setSubType(subType);
    }

    public EndpointProperties getProperties() {
        return properties;
    }

    public <T extends EndpointProperties> T getProperties(Class<T> propertiesClass) {
        if (!propertiesClass.isInstance(properties)) {
            throw new IllegalStateException("Endpoint properties type mismatch, expected: " + propertiesClass.getName() +
                    ", actual: " + properties.getClass().getName());
        } else {
            return propertiesClass.cast(properties);
        }
    }

    public void setProperties(EndpointProperties properties) {
        this.properties = properties;
    }

    public Set<BehaviorGroupAction> getBehaviorGroupActions() {
        return behaviorGroupActions;
    }

    public void setBehaviorGroupActions(Set<BehaviorGroupAction> behaviorGroupActions) {
        this.behaviorGroupActions = behaviorGroupActions;
    }

    public Set<NotificationHistory> getNotificationHistories() {
        return notificationHistories;
    }

    public void setNotificationHistories(Set<NotificationHistory> notificationHistories) {
        this.notificationHistories = notificationHistories;
    }

    public EndpointStatus getStatus() {
        return status;
    }

    public void setStatus(EndpointStatus status) {
        this.status = status;
    }

    public int getServerErrors() {
        return serverErrors;
    }

    public void setServerErrors(int serverErrors) {
        this.serverErrors = serverErrors;
    }

    public LocalDateTime getServerErrorsSince() {
        return serverErrorsSince;
    }

    public void setServerErrorsSince(LocalDateTime serverErrorsSince) {
        this.serverErrorsSince = serverErrorsSince;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof Endpoint) {
            Endpoint other = (Endpoint) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Endpoint{" +
                "id=" + id +
                ", accountId='" + accountId + '\'' +
                ", orgId='" + orgId + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", enabled=" + enabled +
                ", type=" + compositeType.getType() +
                ", subType=" + compositeType.getSubType() +
                ", status=" + status + '\'' +
                ", created=" + getCreated() +
                ", updated=" + getUpdated() +
                ", properties=" + properties +
                ", serverErrors=" + serverErrors +
                ", serverErrorsSince=" + serverErrorsSince +
                '}';
    }
}
