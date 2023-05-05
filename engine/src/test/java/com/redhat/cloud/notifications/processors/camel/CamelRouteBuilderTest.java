package com.redhat.cloud.notifications.processors.camel;

import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class CamelRouteBuilderTest {

    public static CamelNotification buildNotification(String webhookUrl) {
        CamelNotification notification = new CamelNotification();
        notification.orgId = DEFAULT_ORG_ID;
        notification.historyId = UUID.randomUUID();
        notification.webhookUrl = webhookUrl;
        notification.message = "This is a test!";
        return notification;
    }
}
