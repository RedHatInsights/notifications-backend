package com.redhat.cloud.notifications.processors.email;

public class EmailPendo {
    private String pendoTitle;
    private String pendoMessage;

    public EmailPendo(String pendoTitle, String pendoMessage) {
        this.pendoTitle = pendoTitle;
        this.pendoMessage = pendoMessage;
    }

    public String getPendoTitle() {
        return pendoTitle;
    }

    public String getPendoMessage() {
        return pendoMessage;
    }
}
