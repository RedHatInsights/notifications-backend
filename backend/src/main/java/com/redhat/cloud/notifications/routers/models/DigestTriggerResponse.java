package com.redhat.cloud.notifications.routers.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.models.dto.v3.subscriptions.SubscriptionTypeDTO;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

@JsonNaming(SnakeCaseStrategy.class)
public class DigestTriggerResponse {

    private SubscriptionTypeDTO subscriptionType;

    private LocalTime scheduledExecutionTime;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private DayOfWeek scheduledExecutionDay;

    private LocalDateTime nextRun;

    public SubscriptionTypeDTO getSubscriptionType() {
        return subscriptionType;
    }

    public void setSubscriptionType(SubscriptionTypeDTO subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    public LocalTime getScheduledExecutionTime() {
        return scheduledExecutionTime;
    }

    public void setScheduledExecutionTime(LocalTime scheduledExecutionTime) {
        this.scheduledExecutionTime = scheduledExecutionTime;
    }

    public DayOfWeek getScheduledExecutionDay() {
        return scheduledExecutionDay;
    }

    public void setScheduledExecutionDay(DayOfWeek scheduledExecutionDay) {
        this.scheduledExecutionDay = scheduledExecutionDay;
    }

    public LocalDateTime getNextRun() {
        return nextRun;
    }

    public void setNextRun(LocalDateTime nextRun) {
        this.nextRun = nextRun;
    }
}
