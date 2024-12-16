package com.redhat.cloud.notifications.processors.drawer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.repositories.BundleRepository;
import com.redhat.cloud.notifications.db.repositories.DrawerNotificationRepository;
import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.models.DrawerEntryPayload;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.IntegrationTemplate;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.RecipientsAuthorizationCriterionExtractor;
import com.redhat.cloud.notifications.processors.SystemEndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.email.connector.dto.RecipientSettings;
import com.redhat.cloud.notifications.templates.TemplateService;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.quarkus.cache.CacheResult;
import io.quarkus.qute.TemplateInstance;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.IntegrationTemplate.TemplateKind.ALL;
import static com.redhat.cloud.notifications.models.SubscriptionType.DRAWER;

@ApplicationScoped
public class DrawerProcessor extends SystemEndpointTypeProcessor {

    @Inject
    TemplateRepository templateRepository;

    @Inject
    TemplateService templateService;

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    DrawerNotificationRepository drawerNotificationRepository;

    @Inject
    EventRepository eventRepository;

    @Inject
    EngineConfig engineConfig;

    @Inject
    ConnectorSender connectorSender;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    NotificationHistoryRepository notificationHistoryRepository;

    @Inject
    BundleRepository bundleRepository;

    @Inject
    RecipientsAuthorizationCriterionExtractor recipientsAuthorizationCriterionExtractor;

    @Override
    public void process(Event event, List<Endpoint> endpoints) {
        if (!engineConfig.isDrawerEnabled()) {
            return;
        }
        if (endpoints == null || endpoints.isEmpty()) {
            return;
        }

        // build event thought qute template
        String renderedData = buildNotificationMessage(event);

        // store it on event table
        event.setRenderedDrawerNotification(renderedData);
        eventRepository.updateDrawerNotification(event);

        // get default endpoint
        DrawerEntryPayload drawerEntryPayload = buildJsonPayloadFromEvent(event);

        final Set<String> unsubscribers =
                Set.copyOf(subscriptionRepository.getUnsubscribers(event.getOrgId(), event.getEventType().getId(), DRAWER));
        final Set<RecipientSettings> recipientSettings = extractAndTransformRecipientSettings(event, endpoints);

        // Prepare all the data to be sent to the connector.
        final DrawerNotificationToConnector drawerNotificationToConnector = new DrawerNotificationToConnector(
            event.getOrgId(),
            drawerEntryPayload,
            recipientSettings,
            unsubscribers,
            recipientsAuthorizationCriterionExtractor.extract(event)
        );

        connectorSender.send(event, endpoints.getFirst(), JsonObject.mapFrom(drawerNotificationToConnector));
    }

    private DrawerEntryPayload buildJsonPayloadFromEvent(Event event) {
        DrawerEntryPayload drawerEntryPayload = new DrawerEntryPayload();
        drawerEntryPayload.setDescription(event.getRenderedDrawerNotification());
        drawerEntryPayload.setTitle(event.getEventTypeDisplayName());
        drawerEntryPayload.setCreated(event.getCreated());
        drawerEntryPayload.setSource(String.format("%s - %s", event.getApplicationDisplayName(), event.getBundleDisplayName()));
        drawerEntryPayload.setBundle(bundleRepository.getBundle(event.getBundleId()).getName());
        drawerEntryPayload.setEventId(event.getId());
        return drawerEntryPayload;
    }

    public String buildNotificationMessage(Event event) {
        JsonObject data = baseTransformer.toJsonObject(event);

        Map<Object, Object> dataAsMap;
        try {
            dataAsMap = objectMapper.readValue(data.encode(), Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Drawer notification data transformation failed", e);
        }

        String message = getTemplate(event.getApplicationId(), event.getEventType().getId(), event.getOrgId())
            .data("data", dataAsMap)
            .render().trim();

        return message;
    }

    @CacheResult(cacheName = "drawer-template")
    TemplateInstance getTemplate(UUID applicationId, UUID eventTypeId, String orgId) {
        IntegrationTemplate integrationTemplate = templateRepository.findIntegrationTemplate(applicationId, eventTypeId, orgId, ALL, "drawer")
            .orElseThrow(() -> new IllegalStateException("No default template defined for drawer"));
        String template = integrationTemplate.getTheTemplate().getData();
        return templateService.compileTemplate(template, integrationTemplate.getTheTemplate().getName());
    }

    public void manageConnectorDrawerReturnsIfNeeded(Map<String, Object> decodedPayload, UUID historyId) {
        Map<String, Object> details = (HashMap<String, Object>) decodedPayload.get("details");
        if (null != details && "com.redhat.console.notification.toCamel.drawer".equals(details.get("type"))) {
            com.redhat.cloud.notifications.models.Event event = notificationHistoryRepository.getEventIdFromHistoryId(historyId);
            List<String> recipients = (List<String>) details.get("resolved_recipient_list");
            if (null != recipients && recipients.size() > 0) {
                String drawerNotificationIds = String.join(",", recipients);
                drawerNotificationRepository.create(event, drawerNotificationIds);
                details.remove("resolved_recipient_list");
                details.put("new_drawer_entry_counter", recipients.size());
            }
        }
    }
}
