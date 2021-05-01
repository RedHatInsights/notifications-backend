package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.models.filter.ApiResponseFilter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.hibernate.jpa.QueryHints;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.QueryHint;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
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
/*
 * When PostgreSQL sorts a BOOLEAN column in DESC order, true comes first. That's not true for all DBMS.
 *
 * When QueryHints.HINT_PASS_DISTINCT_THROUGH is set to false, Hibernate returns distinct results without passing the
 * DISTINCT keyword to the DBMS. This is better for performances.
 * See https://in.relation.to/2016/08/04/introducing-distinct-pass-through-query-hint/ for more details about that hint.
 */
// TODO Move this query back to BehaviorGroupResources when hints are implemented in Mutiny.Query.
@NamedQuery(
        name = "findByBundleId",
        query = "SELECT DISTINCT b FROM BehaviorGroup b LEFT JOIN FETCH b.actions a " +
                "WHERE b.accountId = :accountId AND b.bundle.id = :bundleId " +
                "ORDER BY b.defaultBehavior DESC, b.created DESC, a.position ASC",
        hints = @QueryHint(name = QueryHints.HINT_PASS_DISTINCT_THROUGH, value = "false")
)
public class BehaviorGroup extends CreationUpdateTimestamped {

    @Id
    @GeneratedValue
    @JsonProperty(access = READ_ONLY)
    private UUID id;

    @Size(max = 50)
    @JsonIgnore
    private String accountId;

    @NotNull
    @Pattern(regexp = "[a-z][a-z_0-9-]*")
    @Size(max = 255)
    private String name;

    @NotNull
    @JsonProperty(value = "display_name")
    private String displayName;

    @NotNull
    @Transient
    @Schema(name = "bundle_id")
    private UUID bundleId;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "bundle_id")
    @JsonIgnore
    private Bundle bundle;

    /*
     * Is this behavior group the default one for its bundle?
     * There should never be more than one default behavior group for each bundle.
     */
    @JsonProperty(value = "default_behavior", access = READ_ONLY)
    private boolean defaultBehavior;

    @OneToMany(mappedBy = "behaviorGroup", cascade = CascadeType.REMOVE)
    @JsonInclude(Include.NON_NULL)
    private List<BehaviorGroupAction> actions;

    @Transient
    @JsonIgnore
    private boolean filterOutActions;

    @OneToMany(mappedBy = "behaviorGroup", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private Set<EventTypeBehavior> behaviors;

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

    public Boolean getDefaultBehavior() {
        return defaultBehavior;
    }

    public void setDefaultBehavior(boolean defaultBehavior) {
        this.defaultBehavior = defaultBehavior;
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
