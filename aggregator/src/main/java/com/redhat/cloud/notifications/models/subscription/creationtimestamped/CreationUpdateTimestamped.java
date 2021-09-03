package com.redhat.cloud.notifications.models.subscription.creationtimestamped;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.MappedSuperclass;
import javax.persistence.PreUpdate;
import java.time.LocalDateTime;

import static com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY;

@MappedSuperclass
public abstract class CreationUpdateTimestamped extends CreationTimestamped {

    @JsonProperty(access = READ_ONLY)
    @JsonInclude(NON_NULL)
    @JsonFormat(shape = STRING)
    private LocalDateTime updated;

    public LocalDateTime getUpdated() {
        return updated;
    }

    public void setUpdated(LocalDateTime updated) {
        this.updated = updated;
    }

    @PreUpdate
    public void preUpdate() {
        updated = LocalDateTime.now(UTC);
    }
}
