package com.redhat.cloud.notifications.routers.models;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
public class UserConfigPreferences {
    private Boolean instantEmail;
    private Boolean dailyEmail;

    public Boolean getInstantEmail() {
        return instantEmail;
    }

    public void setInstantEmail(Boolean instantEmail) {
        this.instantEmail = instantEmail;
    }

    public Boolean getDailyEmail() {
        return dailyEmail;
    }

    public void setDailyEmail(Boolean dailyEmail) {
        this.dailyEmail = dailyEmail;
    }
}
