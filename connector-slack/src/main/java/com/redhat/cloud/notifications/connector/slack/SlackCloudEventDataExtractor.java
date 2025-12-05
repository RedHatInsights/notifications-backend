package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.connector.slack.config.SlackConnectorConfig;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;

@ApplicationScoped
public class SlackCloudEventDataExtractor extends CloudEventDataExtractor {

    @Inject
    TemplateService templateService;

    @Inject
    SlackConnectorConfig connectorConfig;

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) {
        CamelNotification notification =  cloudEventData.mapTo(CamelNotification.class);
        exchange.setProperty(TARGET_URL, notification.webhookUrl);

        final String bundle = notification.eventData.get("bundle").toString();
        final String application = notification.eventData.get("application").toString();
        final String eventType = notification.eventData.get("event_type").toString();
        boolean useBetaTemplate = connectorConfig.isUseBetaTemplatesEnabled(exchange.getProperty(ORG_ID, String.class), bundle, application, eventType);

        TemplateDefinition templateDefinition = new TemplateDefinition(
            IntegrationType.SLACK,
            bundle,
            application,
            eventType,
            useBetaTemplate);
        String sourceMessage = templateService.renderTemplate(templateDefinition, notification.eventData);

        JsonObject message = new JsonObject();
        try {
            message = new JsonObject(sourceMessage);
        } catch (DecodeException e) {
            message.put("text", sourceMessage);
        }

        if (cloudEventData.getValue("channel") != null) {
            message.put("channel", cloudEventData.getString("channel"));
        }
        exchange.getIn().setBody(message.encode());
    }
}
