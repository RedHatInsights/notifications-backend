package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.models.filter.ApiResponseFilter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
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

    @Id
    @GeneratedValue
    @JsonProperty(access = READ_ONLY)
    private UUID id;

    @NotNull
    @Pattern(regexp = "[a-z][a-z_0-9-]*")
    @Size(max = 255)
    private String name;

    @NotNull
    @Schema(name = "display_name")
    private String displayName;

    @JsonInclude(NON_NULL)
    private String description;

    @NotNull
    @Transient
    @Schema(name = "application_id")
    private UUID applicationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id")
    @JsonInclude(NON_NULL)
    private Application application;

    @Transient
    @JsonIgnore
    private boolean filterOutApplication;

    // TODO [BG Phase 2] Delete this attribute
    @OneToMany(mappedBy = "eventType", cascade = CascadeType.ALL)
    @JsonIgnore
    private Set<EndpointTarget> targets;

    @OneToMany(mappedBy = "eventType", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private Set<EventTypeBehavior> behaviors;

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

    // TODO [BG Phase 2] Delete this method
    public Set<EndpointTarget> getTargets() {
        return targets;
    }

    // TODO [BG Phase 2] Delete this method
    public void setTargets(Set<EndpointTarget> targets) {
        this.targets = targets;
    }

    public Set<EventTypeBehavior> getBehaviors() {
        return behaviors;
    }

    public void setBehaviors(Set<EventTypeBehavior> behaviors) {
        this.behaviors = behaviors;
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
