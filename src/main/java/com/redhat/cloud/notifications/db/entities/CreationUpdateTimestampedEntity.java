package com.redhat.cloud.notifications.db.entities;

import javax.persistence.MappedSuperclass;
import javax.persistence.PreUpdate;
import java.time.LocalDateTime;

@MappedSuperclass
public abstract class CreationUpdateTimestampedEntity extends CreationTimestampedEntity {

    public LocalDateTime updated;

    @PreUpdate
    public void preUpdate() {
        updated = LocalDateTime.now(UTC);
    }
}
