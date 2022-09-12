package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.models.filter.ApiResponseFilter;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;
import static com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY;
import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import static javax.persistence.FetchType.LAZY;

@Entity
@Table(name = "behavior_group")
@JsonNaming(SnakeCaseStrategy.class)
@JsonFilter(ApiResponseFilter.NAME)
public class BehaviorGroup extends CreationUpdateTimestamped {

    public static final String[] SORT_FIELDS = {"displayName"};

    @Id
    @GeneratedValue
    @JsonProperty(access = READ_ONLY)
    private UUID id;

    @Size(max = 50)
    @JsonIgnore
    private String accountId;

    @Size(max = 50)
    @JsonIgnore
    private String orgId;

    @NotNull
    @NotBlank
    private String displayName;

    @NotNull
    @Transient
    private UUID bundleId;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "bundle_id")
    @JsonInclude(Include.NON_NULL)
    private Bundle bundle;

    @Transient
    @JsonIgnore
    private boolean filterOutBundle;

    @OneToMany(mappedBy = "behaviorGroup", cascade = CascadeType.REMOVE)
    @JsonInclude(Include.NON_NULL)
    private List<BehaviorGroupAction> actions;

    @Transient
    @JsonIgnore
    private boolean filterOutActions;

    @OneToMany(mappedBy = "behaviorGroup", cascade = CascadeType.REMOVE)
    private Set<EventTypeBehavior> behaviors;

    @Transient
    @JsonIgnore
    private boolean filterOutBehaviors;

    @JsonInclude
    @JsonProperty(access = READ_ONLY, value = "default_behavior")
    public boolean isDefaultBehavior() {
        return accountId == null && orgId == null;
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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public UUID getBundleId() {
        if (bundleId == null && bundle != null) {
            bundleId = bundle.getId();
        }
        return bundleId;
    }

    public void setBundleId(UUID bundleId) {
        this.bundleId = bundleId;
    }

    public Bundle getBundle() {
        return bundle;
    }

    public void setBundle(Bundle bundle) {
        this.bundle = bundle;
    }

    public boolean isFilterOutBundle() {
        return filterOutBundle;
    }

    public BehaviorGroup filterOutBundle() {
        filterOutBundle = true;
        return this;
    }

    public List<BehaviorGroupAction> getActions() {
        return actions;
    }

    public void setActions(List<BehaviorGroupAction> actions) {
        this.actions = actions;
    }

    public boolean isFilterOutActions() {
        return filterOutActions;
    }

    public BehaviorGroup filterOutActions() {
        filterOutActions = true;
        return this;
    }

    public Set<EventTypeBehavior> getBehaviors() {
        return behaviors;
    }

    public void setBehaviors(Set<EventTypeBehavior> behaviors) {
        this.behaviors = behaviors;
    }

    public boolean isFilterOutBehaviors() {
        return filterOutBehaviors;
    }

    public BehaviorGroup filterOutBehaviors() {
        filterOutBehaviors = true;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof BehaviorGroup) {
            BehaviorGroup other = (BehaviorGroup) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
