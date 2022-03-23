package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.util.Objects;
import java.util.UUID;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@Entity
@Table(name = "internal_role_access")
@JsonNaming(SnakeCaseStrategy.class)
public class InternalRoleAccess {

    public static final String INTERNAL_ROLE_PREFIX = "internal-role:";

    @Id
    @GeneratedValue
    private UUID id;

    @NotNull
    @Size(max = 200)
    private String role;

    @NotNull
    @Transient
    private UUID applicationId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "application_id")
    @JsonIgnore
    private Application application;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public UUID getApplicationId() {
        if (applicationId == null && application != null) {
            applicationId = application.getId();
        }
        return applicationId;
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public Application getApplication() {
        return this.application;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    @JsonIgnore
    public String getInternalRole() {
        return InternalRoleAccess.getInternalRole(this.role);
    }

    public static String getInternalRole(String role) {
        return INTERNAL_ROLE_PREFIX + role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof InternalRoleAccess) {
            InternalRoleAccess other = (InternalRoleAccess) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
