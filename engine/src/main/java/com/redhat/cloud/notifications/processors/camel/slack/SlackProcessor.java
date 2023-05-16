package com.redhat.cloud.notifications.processors.camel.slack;

import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.processors.camel.CamelProcessor;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.UUID;


@ApplicationScoped
public class SlackProcessor extends CamelProcessor {

    @Inject
    @RestClient
    InternalTemporarySlackService internalTemporarySlackService;

    @Override
    protected String getIntegrationName() {
        return "Slack";
    }

    @Override
    protected String getIntegrationType() {
        return "slack";
    }

    @Override
    protected void sendNotification(Event event, Endpoint endpoint, UUID historyId) throws Exception {
        String message = buildNotificationMessage(event);
        CamelProperties properties = endpoint.getProperties(CamelProperties.class);

        SlackNotification notification = new SlackNotification();
        notification.orgId = endpoint.getOrgId();
        notification.historyId = historyId;
        notification.webhookUrl = properties.getUrl();
        notification.channel = properties.getExtras().get("channel");
        notification.message = message;

        internalTemporarySlackService.send(notification);
    }
}
