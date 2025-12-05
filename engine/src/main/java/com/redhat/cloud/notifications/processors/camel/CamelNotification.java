package com.redhat.cloud.notifications.processors.camel;

import java.util.Map;

public class CamelNotification {

    public String webhookUrl;

    public String message;

    public Map<String, Object> eventData;
}
