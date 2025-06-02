package com.redhat.cloud.notifications.processors.camel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.DelayedThrower;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.InsightsUrlsBuilder;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.events.EndpointProcessor.DELAYED_EXCEPTION_MSG;

public abstract class CamelProcessor extends EndpointTypeProcessor {

    @Inject
    EngineConfig engineConfig;

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    Environment environment;

    @Inject
    InsightsUrlsBuilder insightsUrlsBuilder;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ConnectorSender connectorSender;

    @Inject
    com.redhat.cloud.notifications.qute.templates.TemplateService quteTemplateService;

    @Override
    public void process(Event event, List<Endpoint> endpoints) {
        if (engineConfig.isEmailsOnlyModeEnabled()) {
            Log.warn("@channel Skipping event processing because Notifications is running in emails only mode");
            return;
        }
        DelayedThrower.throwEventually(DELAYED_EXCEPTION_MSG, accumulator -> {
            for (Endpoint endpoint : endpoints) {
                try {
                    process(event, endpoint);
                } catch (Exception e) {
                    accumulator.add(e);
                }
            }
        });
    }

    private void process(Event event, Endpoint endpoint) {

        CamelNotification notification = getCamelNotification(event, endpoint);
        JsonObject payload = JsonObject.mapFrom(notification);

        connectorSender.send(event, endpoint, payload);
    }

    protected String buildNotificationMessage(Event event) {
        JsonObject data = baseTransformer.toJsonObject(event);
        insightsUrlsBuilder.buildInventoryUrl(data, getIntegrationType()).ifPresent(url -> data.put("inventory_url", url));
        data.put("application_url", insightsUrlsBuilder.buildApplicationUrl(data, getIntegrationType()));

        JsonObject context = data.getJsonObject("context");
        if (context != null) {
            context.put("environment_url", environment.url());
        } else {
            context = JsonObject.of("environment_url", environment.url());
        }
        data.put("context", context);

        Map<String, Object> dataAsMap;
        try {
            dataAsMap = objectMapper.readValue(data.encode(), Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(getIntegrationName() + " notification data transformation failed", e);
        }

        boolean shouldUseBetaVersion = engineConfig.isUseBetaTemplatesEnabled(event.getOrgId(), event.getEventType().getId());

        TemplateDefinition templateDefinition = new TemplateDefinition(
            getQuteIntegrationType(),
            event.getEventType().getApplication().getBundle().getName(),
            event.getEventType().getApplication().getName(),
            event.getEventType().getName(),
            shouldUseBetaVersion);

        return quteTemplateService.renderTemplate(templateDefinition, dataAsMap);
    }

    protected CamelNotification getCamelNotification(Event event, Endpoint endpoint) {
        String message = buildNotificationMessage(event);
        CamelProperties properties = endpoint.getProperties(CamelProperties.class);

        CamelNotification notification = new CamelNotification();
        notification.webhookUrl = properties.getUrl();
        notification.message = message;
        return notification;
    }

    protected abstract String getIntegrationName();

    protected abstract String getIntegrationType();

    protected IntegrationType getQuteIntegrationType() {
        return IntegrationType.valueOf(getIntegrationType().toUpperCase());
    }
}
