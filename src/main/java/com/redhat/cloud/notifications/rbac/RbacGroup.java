package com.redhat.cloud.notifications.rbac;

import java.time.LocalDateTime;
import java.util.UUID;

public class RbacGroup {

    public String name;
    public String description;
    public UUID uuid;
    public LocalDateTime created;
    public LocalDateTime modified;
    public Integer principalCount;
    public Integer roleCount;
    public boolean system;
    public boolean platformDefault;

}
