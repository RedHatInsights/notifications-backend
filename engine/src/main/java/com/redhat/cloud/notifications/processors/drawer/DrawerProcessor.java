package com.redhat.cloud.notifications.processors.drawer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.DelayedThrower;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.repositories.DrawerNotificationRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.models.DrawerEntry;
import com.redhat.cloud.notifications.models.DrawerEntryPayload;
import com.redhat.cloud.notifications.models.DrawerNotification;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.IntegrationTemplate;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.SystemEndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.email.connector.dto.RecipientSettings;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.templates.TemplateService;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.events.EndpointProcessor.DELAYED_EXCEPTION_MSG;
import static com.redhat.cloud.notifications.models.IntegrationTemplate.TemplateKind.ALL;
import static com.redhat.cloud.notifications.models.NotificationHistory.getHistoryStub;

@ApplicationScoped
public class DrawerProcessor extends SystemEndpointTypeProcessor {

    public static final String DRAWER_CHANNEL = "drawer";

    public static final String CLOUD_EVENT_TYPE_PREFIX = "com.redhat.console.notifications.drawer";

    @Inject
    TemplateRepository templateRepository;

    @Inject
    TemplateService templateService;

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    DrawerNotificationRepository drawerNotificationRepository;

    @Inject
    EventRepository eventRepository;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    @Channel(DRAWER_CHANNEL)
    Emitter<JsonObject> emitter;

    @Inject
    ConnectorSender connectorSender;

    @Override
    public void process(Event event, List<Endpoint> endpoints) {
        if (!featureFlipper.isDrawerEnabled()) {
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
        Endpoint endpoint = endpointRepository.getOrCreateDefaultSystemSubscription(event.getAccountId(), event.getOrgId(), EndpointType.DRAWER);

        if (featureFlipper.isDrawerConnectorEnabled()) {
            DrawerEntryPayload drawerEntryPayload = buildJsonPayloadFromEvent(event);

            final Set<String> subscribers = Set.copyOf(getSubscribers(event, SubscriptionType.DRAWER));
            final Set<RecipientSettings> recipientSettings = extractAndTransformRecipientSettings(event, endpoints);

            // Prepare all the data to be sent to the connector.
            final DrawerNotificationToConnector drawerNotificationToConnector = new DrawerNotificationToConnector(
                event.getOrgId(),
                drawerEntryPayload,
                recipientSettings,
                subscribers
            );

            connectorSender.send(event, endpoint, JsonObject.mapFrom(drawerNotificationToConnector));
        } else {
            Set<User> userList = getRecipientList(event, endpoints, SubscriptionType.DRAWER);
            if (null == userList || userList.isEmpty()) {
                return;
            }

            UUID historyId = UUID.randomUUID();
            Log.infof("Processing drawer notification [orgId=%s, eventId=%s, historyId=%s]",
                event.getOrgId(), event.getId(), historyId);

            long startTime = System.currentTimeMillis();

            NotificationHistory history = null;
            try {
                String userNameListAsStr = userList.stream().map(usr -> usr.getId()).collect(Collectors.joining(","));
                List<DrawerNotification> drawerNotifications = drawerNotificationRepository.create(event, userNameListAsStr);

                DelayedThrower.throwEventually(DELAYED_EXCEPTION_MSG, accumulator -> {
                    for (DrawerNotification drawer : drawerNotifications) {
                        try {
                            JsonObject payload = buildJsonPayload(drawer, event);
                            sendIt(payload);
                        } catch (Exception e) {
                            accumulator.add(e);
                        }
                    }
                });

                history = getHistoryStub(endpoint, event, 0L, historyId);
                history.setStatus(NotificationStatus.SUCCESS);
            } catch (Exception e) {
                history = getHistoryStub(endpoint, event, 0L, historyId);
                history.setStatus(NotificationStatus.FAILED_INTERNAL);
                history.setDetails(Map.of("failure", e.getMessage()));
                Log.infof(e, "Processing drawer notification failed [eventId=%s, historyId=%s]", event.getId(), historyId);
            } finally {
                long invocationTime = System.currentTimeMillis() - startTime;
                history.setInvocationTime(invocationTime);
                persistNotificationHistory(history);
            }
        }
    }

    private DrawerEntryPayload buildJsonPayloadFromEvent(Event event) {
        DrawerEntryPayload drawerEntryPayload = new DrawerEntryPayload();
        drawerEntryPayload.setDescription(event.getRenderedDrawerNotification());
        drawerEntryPayload.setTitle(event.getEventTypeDisplayName());
        drawerEntryPayload.setCreated(event.getCreated());
        drawerEntryPayload.setSource(String.format("%s - %s", event.getApplicationDisplayName(), event.getBundleDisplayName()));
        return drawerEntryPayload;
    }

    private JsonObject buildJsonPayload(DrawerNotification notif, Event event) {
        DrawerEntryPayload drawerEntryPayload = buildJsonPayloadFromEvent(event);
        drawerEntryPayload.setId(notif.getId());
        drawerEntryPayload.setRead(notif.isRead());

        DrawerEntry drawerEntry = new DrawerEntry();
        drawerEntry.setOrganizations(List.of(notif.getOrgId()));
        drawerEntry.setUsers(List.of(notif.getUserId()));
        drawerEntry.setPayload(drawerEntryPayload);
        return JsonObject.mapFrom(drawerEntry);
    }

    private void sendIt(JsonObject payload) {
        CloudEventMetadata<String> cloudEventMetadata = OutgoingCloudEventMetadata.<String>builder()
            .withId(UUID.randomUUID().toString())
            .withType(CLOUD_EVENT_TYPE_PREFIX)
            .withDataContentType(MediaType.APPLICATION_JSON)
            .withSpecVersion("1.0.2")
            .build();

        Log.infof("Encoded Payload: %s", payload.encode());
        Message<JsonObject> message = Message.of(payload)
            .addMetadata(cloudEventMetadata);

        emitter.send(message);
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
}
