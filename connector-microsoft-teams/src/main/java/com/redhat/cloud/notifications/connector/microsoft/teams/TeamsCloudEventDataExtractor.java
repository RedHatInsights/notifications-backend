package com.redhat.cloud.notifications.connector.microsoft.teams;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
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
        TemplateDefinition templateDefinition = new TemplateDefinition(
            IntegrationType.MS_TEAMS,
            notification.eventData.get("bundle").toString(),
            notification.eventData.get("application").toString(),
            notification.eventData.get("event_type").toString());
        exchange.getIn().setBody(templateService.renderTemplate(templateDefinition, notification.eventData));
    }
}
