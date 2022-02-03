package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import java.util.UUID;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@Entity
@Table(name = "internal_role_access")
@JsonNaming(SnakeCaseStrategy.class)
public class InternalRoleAccess {

    @Id
    @GeneratedValue
    private UUID id;

    private String role;

    @NotNull
    private UUID applicationId;

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
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public String getPrivateRole() {
        return InternalRoleAccess.getPrivateRole(this.role);
    }

    public static String getPrivateRole(String role) {
        return String.format("internal-role:%s", role);
    }

    public static String getPrivateRolePrefix() {
        return getPrivateRole("");
    }
}
