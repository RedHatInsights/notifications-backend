package com.redhat.cloud.notifications.routers.models;

public class SubscriptionSettings {
    private boolean dailyEmail;
    private boolean instantEmail;


    public boolean isDailyEmail() {
        return dailyEmail;
    }

    public void setDailyEmail(boolean dailyEmail) {
        this.dailyEmail = dailyEmail;
    }

    public boolean isInstantEmail() {
        return instantEmail;
    }

    public void setInstantEmail(boolean instantEmail) {
        this.instantEmail = instantEmail;
    }
}
