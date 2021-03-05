package com.redhat.cloud.notifications.db.entities;

import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.ZoneId;

@MappedSuperclass
public abstract class CreationTimestampedEntity {

    protected static final ZoneId UTC = ZoneId.of("UTC");

    @NotNull
    public LocalDateTime created;

    @PrePersist
    public void prePersist() {
        created = LocalDateTime.now(UTC);
    }
}
