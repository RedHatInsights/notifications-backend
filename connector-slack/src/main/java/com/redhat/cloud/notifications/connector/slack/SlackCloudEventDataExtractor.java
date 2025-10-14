package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;

@ApplicationScoped
public class SlackCloudEventDataExtractor extends CloudEventDataExtractor {

    @Inject
    TemplateService templateService;

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) {
        CamelNotification notification =  cloudEventData.mapTo(CamelNotification.class);
        exchange.setProperty(TARGET_URL, notification.webhookUrl);

        TemplateDefinition templateDefinition = new TemplateDefinition(
            IntegrationType.SLACK,
            notification.eventData.get("bundle").toString(),
            notification.eventData.get("application").toString(),
            notification.eventData.get("event_type").toString());
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
