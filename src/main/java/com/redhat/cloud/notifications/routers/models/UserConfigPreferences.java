package com.redhat.cloud.notifications.routers.models;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@JsonNaming(SnakeCaseStrategy.class)
public class UserConfigPreferences {
    @Schema(name = "instant_email")
    private Boolean instantEmail;
    @Schema(name = "daily_email")
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
