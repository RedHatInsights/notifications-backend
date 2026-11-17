package com.redhat.cloud.notifications.models;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "digest_trigger_org_config")
public class DigestTriggerOrgConfig {

    @EmbeddedId
    private DigestTriggerOrgConfigId id;

    @NotNull
    @Size(max = 100)
    private String cronExpression;

    private LocalDateTime lastRun;

    @NotNull
    private LocalDateTime nextRun;

    @NotNull
    @Size(max = 20)
    private String status = "PENDING";

    private LocalDateTime claimedAt;

    public DigestTriggerOrgConfig() {
    }

    public DigestTriggerOrgConfig(DigestTriggerOrgConfigId id, String cronExpression) {
        this.id = id;
        this.cronExpression = cronExpression;
    }

    public DigestTriggerOrgConfigId getId() {
        return id;
    }

    public void setId(DigestTriggerOrgConfigId id) {
        this.id = id;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public LocalDateTime getLastRun() {
        return lastRun;
    }

    public void setLastRun(LocalDateTime lastRun) {
        this.lastRun = lastRun;
    }

    public LocalDateTime getNextRun() {
        return nextRun;
    }

    public void setNextRun(LocalDateTime nextRun) {
        this.nextRun = nextRun;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(LocalDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DigestTriggerOrgConfig that = (DigestTriggerOrgConfig) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
