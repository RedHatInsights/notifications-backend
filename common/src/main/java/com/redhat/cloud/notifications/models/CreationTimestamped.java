package com.redhat.cloud.notifications.models;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

import java.time.LocalDateTime;
import java.time.ZoneId;

@MappedSuperclass
public abstract class CreationTimestamped {

    protected static final ZoneId UTC = ZoneId.of("UTC");

    private LocalDateTime created;

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    @PrePersist
    public final void prePersist() {
        // The 'created' field value can be set in tests.
        if (created == null) {
            created = LocalDateTime.now(UTC);
        }
        additionalPrePersist();
    }

    /**
     * This method can be overridden from an entity to add additional instructions that should be
     * executed before the entity is first persisted.
     */
    protected void additionalPrePersist() {
    }
}
