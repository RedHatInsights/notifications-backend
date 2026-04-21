package com.redhat.cloud.notifications.connector.google.chat;

import com.redhat.cloud.notifications.connector.v2.models.NotificationToConnector;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public class GoogleChatNotification extends NotificationToConnector {

    @NotNull
    @NotBlank
    public String webhookUrl;

    @NotNull
    public Map<String, Object> eventData;
}
