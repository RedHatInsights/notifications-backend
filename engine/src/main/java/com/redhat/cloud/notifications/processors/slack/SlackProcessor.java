package com.redhat.cloud.notifications.processors.slack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.DelayedThrower;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.IntegrationTemplate;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.templates.TemplateService;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.quarkus.qute.TemplateInstance;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.events.EndpointProcessor.DELAYED_EXCEPTION_MSG;
import static com.redhat.cloud.notifications.models.IntegrationTemplate.TemplateKind.ORG;
import static com.redhat.cloud.notifications.models.NotificationHistory.getHistoryStub;

@ApplicationScoped
public class SlackProcessor extends EndpointTypeProcessor {

    public static final String PROCESSED_COUNTER_NAME = "processor.slack.processed";

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    MeterRegistry registry;

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
    @RestClient
    InternalTemporarySlackService internalTemporarySlackService;

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
        registry.counter(PROCESSED_COUNTER_NAME, "subType", endpoint.getSubType()).increment();

        UUID historyId = UUID.randomUUID();

        Log.infof("Sending Slack notification through Camel [orgId=%s, eventId=%s, historyId=%s]",
                endpoint.getOrgId(), event.getId(), historyId);

        long startTime = System.currentTimeMillis();

        SlackNotification notification = buildNotification(event, endpoint, historyId);

        NotificationHistory history = getHistoryStub(endpoint, event, 0L, historyId);
        try {
            internalTemporarySlackService.send(notification);
            history.setStatus(NotificationStatus.SUCCESS);
        } catch (Exception e) {
            history.setStatus(NotificationStatus.FAILED_INTERNAL);
            history.setDetails(Map.of("failure", e.getMessage()));
            Log.infof(e, "Sending Slack notification through Camel failed [eventId=%s, historyId=%s]", event.getId(), historyId);
        }
        long invocationTime = System.currentTimeMillis() - startTime;
        history.setInvocationTime(invocationTime);
        persistNotificationHistory(history);
    }

    private SlackNotification buildNotification(Event event, Endpoint endpoint, UUID historyId) {
        CamelProperties properties = endpoint.getProperties(CamelProperties.class);

        JsonObject data = baseTransformer.toJsonObject(event.getAction());
        data.put("environment_url", environment.url());

        Map<Object, Object> dataAsMap;
        try {
            dataAsMap = objectMapper.readValue(data.encode(), Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Slack notification data transformation failed", e);
        }

        String message = getTemplate(event.getOrgId())
                .data("data", dataAsMap)
                .render();

        SlackNotification notification = new SlackNotification();
        notification.orgId = endpoint.getOrgId();
        notification.historyId = historyId;
        notification.webhookUrl = properties.getUrl();
        notification.channel = properties.getExtras().get("channel");
        notification.message = message;

        return notification;
    }

    private TemplateInstance getTemplate(String orgId) {
        IntegrationTemplate integrationTemplate = templateRepository.findIntegrationTemplate(null, orgId, ORG, "slack")
                .orElseThrow(() -> new IllegalStateException("No default template defined for integration"));
        String template = integrationTemplate.getTheTemplate().getData();
        return templateService.compileTemplate(template, integrationTemplate.getTheTemplate().getName());
    }
}
