package com.redhat.cloud.notifications.recipients.rbac;

import java.time.LocalDateTime;
import java.util.UUID;

public class RbacGroup {

    private String name;
    private String description;
    private UUID uuid;
    private LocalDateTime created;
    private LocalDateTime modified;
    private Integer principalCount;
    private Integer roleCount;
    private boolean system;
    private boolean platformDefault;

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

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getModified() {
        return modified;
    }

    public void setModified(LocalDateTime modified) {
        this.modified = modified;
    }

    public Integer getPrincipalCount() {
        return principalCount;
    }

    public void setPrincipalCount(Integer principalCount) {
        this.principalCount = principalCount;
    }

    public Integer getRoleCount() {
        return roleCount;
    }

    public void setRoleCount(Integer roleCount) {
        this.roleCount = roleCount;
    }

    public boolean isSystem() {
        return system;
    }

    public void setSystem(boolean system) {
        this.system = system;
    }

    public boolean isPlatformDefault() {
        return platformDefault;
    }

    public void setPlatformDefault(boolean platformDefault) {
        this.platformDefault = platformDefault;
    }
}
