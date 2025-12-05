package com.redhat.cloud.notifications.connector.microsoft.teams;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;

@ApplicationScoped
public class TeamsCloudEventDataExtractor extends CloudEventDataExtractor {

    @Inject
    TemplateService templateService;

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) {
        CamelNotification notification =  cloudEventData.mapTo(CamelNotification.class);
        exchange.setProperty(TARGET_URL, notification.webhookUrl);
        if (null != notification.eventData) {
            TemplateDefinition templateDefinition = new TemplateDefinition(
                IntegrationType.MS_TEAMS,
                notification.eventData.get("bundle").toString(),
                notification.eventData.get("application").toString(),
                notification.eventData.get("event_type").toString());
            String templatedEvent = templateService.renderTemplate(templateDefinition, notification.eventData);
            if (!notification.message.equals(templatedEvent)) {
                Log.errorf("Legacy and new rendered messages are different: '%s' vs. '%s'", notification.message, templatedEvent);
                exchange.getIn().setBody(notification.message);
            } else {
                Log.infof("Legacy and new rendered messages are identical");
                exchange.getIn().setBody(templatedEvent);
            }
        } else {
            exchange.getIn().setBody(notification.message);
        }
    }
}
