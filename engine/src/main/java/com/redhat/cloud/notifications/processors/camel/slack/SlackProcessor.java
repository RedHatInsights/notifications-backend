package com.redhat.cloud.notifications.processors.camel.slack;

import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.processors.camel.CamelNotification;
import com.redhat.cloud.notifications.processors.camel.CamelProcessor;
import jakarta.enterprise.context.ApplicationScoped;

import static com.redhat.cloud.notifications.events.EndpointProcessor.SLACK_ENDPOINT_SUBTYPE;

@ApplicationScoped
public class SlackProcessor extends CamelProcessor {

    @Override
    protected String getIntegrationName() {
        return "Slack";
    }

    @Override
    protected String getIntegrationType() {
        return SLACK_ENDPOINT_SUBTYPE;
    }

    @Override
    protected CamelNotification getCamelNotification(Event event, Endpoint endpoint) {
        String message = buildNotificationMessage(event);
        CamelProperties properties = endpoint.getProperties(CamelProperties.class);

        SlackNotification notification = new SlackNotification();
        notification.webhookUrl = properties.getUrl();
        notification.channel = properties.getExtras().get("channel");
        notification.message = message;
        return notification;
    }
}
