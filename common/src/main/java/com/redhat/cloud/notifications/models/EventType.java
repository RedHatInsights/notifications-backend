package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.models.filter.ApiResponseFilter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY;
import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@Entity
@Table(name = "event_type")
@JsonNaming(SnakeCaseStrategy.class)
@JsonFilter(ApiResponseFilter.NAME)
public class EventType {

    public static final Map<String, String> SORT_FIELDS = Map.of(
            "name", "e.name",
            "display_name", "e.displayName",
            "application", "e.application.displayName"
    );

    @Id
    @GeneratedValue
    @JsonProperty(access = READ_ONLY)
    private UUID id;

    @NotNull
    @Pattern(regexp = "[a-z][a-z_0-9-]*")
    @Size(max = 255)
    private String name;

    @NotNull
    private String displayName;

    @JsonInclude(NON_NULL)
    private String description;

    @JsonInclude(NON_NULL)
    @Column(name = "fqn")
    private String fullyQualifiedName;

    @NotNull
    @Transient
    private UUID applicationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id")
    @JsonInclude(NON_NULL)
    private Application application;

    @Transient
    @JsonIgnore
    private boolean filterOutApplication;

    private boolean visible = true;

    private boolean subscribedByDefault;

    private boolean subscriptionLocked;

    private boolean restrictToRecipientsIntegrations;

    @OneToMany(mappedBy = "eventType", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private Set<EventTypeBehavior> behaviors;

    @ManyToMany(mappedBy = "eventTypes")
    @JsonIgnore
    private Set<Endpoint> endpoints;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setFullyQualifiedName(String fullyQualifiedName) {
        this.fullyQualifiedName = fullyQualifiedName;
    }

    public String getFullyQualifiedName() {
        return fullyQualifiedName;
    }

    public UUID getApplicationId() {
        if (applicationId == null && application != null) {
            applicationId = application.getId();
        }
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public boolean isFilterOutApplication() {
        return filterOutApplication;
    }

    public EventType filterOutApplication() {
        filterOutApplication = true;
        return this;
    }

    public Set<EventTypeBehavior> getBehaviors() {
        return behaviors;
    }

    public void setBehaviors(Set<EventTypeBehavior> behaviors) {
        this.behaviors = behaviors;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isSubscribedByDefault() {
        return subscribedByDefault;
    }

    public void setSubscribedByDefault(boolean subscribedByDefault) {
        this.subscribedByDefault = subscribedByDefault;
    }

    public boolean isSubscriptionLocked() {
        return subscriptionLocked;
    }

    public void setSubscriptionLocked(boolean subscriptionLocked) {
        this.subscriptionLocked = subscriptionLocked;
    }

    public boolean isRestrictToRecipientsIntegrations() {
        return restrictToRecipientsIntegrations;
    }

    public void setRestrictToRecipientsIntegrations(boolean restrictToNamedRecipients) {
        this.restrictToRecipientsIntegrations = restrictToNamedRecipients;
    }

    @AssertTrue(message = "The subscription of an event type can only be locked if the event type is subscribed by default")
    private boolean isNotSubscriptionLockedOrSubscribedByDefault() {
        return !subscriptionLocked || subscribedByDefault;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EventType) {
            EventType other = (EventType) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
