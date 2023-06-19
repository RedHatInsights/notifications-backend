package com.redhat.cloud.notifications.processors.camel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.DelayedThrower;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.IntegrationTemplate;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.templates.TemplateService;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.quarkus.logging.Log;
import io.quarkus.qute.TemplateInstance;
import io.vertx.core.json.JsonObject;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.events.EndpointProcessor.DELAYED_EXCEPTION_MSG;
import static com.redhat.cloud.notifications.models.IntegrationTemplate.TemplateKind.ORG;
import static com.redhat.cloud.notifications.models.NotificationHistory.getHistoryStub;
import static com.redhat.cloud.notifications.models.NotificationStatus.FAILED_INTERNAL;
import static com.redhat.cloud.notifications.models.NotificationStatus.PROCESSING;

public abstract class CamelProcessor extends EndpointTypeProcessor {

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    Environment environment;

    @Inject
    TemplateRepository templateRepository;

    @Inject
    TemplateService templateService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    NotificationHistoryRepository notificationHistoryRepository;

    @Inject
    ConnectorSender connectorSender;

    @Override
    public void process(Event event, List<Endpoint> endpoints) {
        if (featureFlipper.isEmailsOnlyMode()) {
            Log.warn("Skipping event processing because Notifications is running in emails only mode");
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

        UUID historyId = UUID.randomUUID();

        Log.infof("Sending %s notification through Camel [orgId=%s, eventId=%s, historyId=%s]",
            getIntegrationName(), endpoint.getOrgId(), event.getId(), historyId);

        NotificationHistory history = getHistoryStub(endpoint, event, 0L, historyId);
        history.setStatus(PROCESSING);
        persistNotificationHistory(history);

        CamelNotification notification = getCamelNotification(event, endpoint);
        JsonObject payload = JsonObject.mapFrom(notification);

        try {
            connectorSender.send(payload, historyId, endpoint.getSubType());
        } catch (Exception e) {
            history.setStatus(FAILED_INTERNAL);
            history.setDetails(Map.of("failure", e.getMessage()));
            notificationHistoryRepository.updateHistoryItem(history);
            Log.infof(e, "Sending %s notification through Camel failed [eventId=%s, historyId=%s]", getIntegrationName(), event.getId(), historyId);
        }
    }

    protected String buildNotificationMessage(Event event) {
        JsonObject data = baseTransformer.toJsonObject(event);
        data.put("environment_url", environment.url());

        Map<Object, Object> dataAsMap;
        try {
            dataAsMap = objectMapper.readValue(data.encode(), Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(getIntegrationName() + " notification data transformation failed", e);
        }

        String message = getTemplate(event.getOrgId())
                .data("data", dataAsMap)
                .render();

        return message;
    }

    private TemplateInstance getTemplate(String orgId) {
        IntegrationTemplate integrationTemplate = templateRepository.findIntegrationTemplate(null, orgId, ORG, getIntegrationType())
                .orElseThrow(() -> new IllegalStateException("No default template defined for integration"));
        String template = integrationTemplate.getTheTemplate().getData();
        return templateService.compileTemplate(template, integrationTemplate.getTheTemplate().getName());
    }

    protected CamelNotification getCamelNotification(Event event, Endpoint endpoint) {
        String message = buildNotificationMessage(event);
        CamelProperties properties = endpoint.getProperties(CamelProperties.class);

        CamelNotification notification = new CamelNotification();
        notification.orgId = endpoint.getOrgId();
        notification.webhookUrl = properties.getUrl();
        notification.message = message;
        return notification;
    }

    protected abstract String getIntegrationName();

    protected abstract String getIntegrationType();
}
