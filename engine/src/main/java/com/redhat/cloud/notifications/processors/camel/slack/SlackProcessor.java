package com.redhat.cloud.notifications.processors.camel.slack;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.processors.camel.CamelNotification;
import com.redhat.cloud.notifications.processors.camel.CamelProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;

import static com.redhat.cloud.notifications.events.EndpointProcessor.SLACK_ENDPOINT_SUBTYPE;

@ApplicationScoped
public class SlackProcessor extends CamelProcessor {

    @Inject
    EngineConfig engineConfig;

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
        Map<String, Object> eventDataAsMap = convertEventAsDataMap(event);
        String message = buildNotificationMessage(event, eventDataAsMap);

        CamelProperties properties = endpoint.getProperties(CamelProperties.class);

        SlackNotification notification = new SlackNotification();
        notification.webhookUrl = properties.getUrl();

        final Map<String, String> extras = properties.getExtras();
        if (null != extras) {
            notification.channel = extras.get("channel");
        }

        notification.message = message;
        if (engineConfig.isConnectorTemplateTransformationEnabled(event.getOrgId())) {
            notification.eventData = eventDataAsMap;
        }
        return notification;
    }
}
