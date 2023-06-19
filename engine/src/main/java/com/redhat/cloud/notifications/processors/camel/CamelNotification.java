package com.redhat.cloud.notifications.processors.camel;

public class CamelNotification {

    public String orgId;

    public String webhookUrl;

    public String message;

    @Override
    public String toString() {
        return "CamelNotification [orgId=" + orgId + ", webhookUrl=" + webhookUrl + "]";
    }
}
