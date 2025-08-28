package com.redhat.cloud.notifications.connector.http;

import java.util.Map;

public class CamelNotification {

    public String webhookUrl;

    public String message;

    public Map<String, Object> eventData;
}
