package com.redhat.cloud.notifications.processors.drawer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.repositories.DrawerNotificationRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.models.DrawerEntry;
import com.redhat.cloud.notifications.models.DrawerNotification;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.IntegrationTemplate;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.processors.SystemEndpointTypeProcessor;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.templates.TemplateService;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.opentelemetry.context.Context;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.reactive.messaging.TracingMetadata;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.IntegrationTemplate.TemplateKind.DEFAULT;
import static com.redhat.cloud.notifications.models.NotificationHistory.getHistoryStub;

@ApplicationScoped
public class DrawerProcessor extends SystemEndpointTypeProcessor {

    public static final String DRAWER_CHANNEL = "todrawer";

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
    Emitter<String> emitter;


    @Override
    public void process(Event event, List<Endpoint> endpoints) {
        if (!featureFlipper.isDrawerEnabled()) {
            return;
        }
        if (endpoints != null && !endpoints.isEmpty()) {
            Set<User> userList = getRecipientList(event, endpoints, EmailSubscriptionType.DRAWER);
            if (null != userList && !userList.isEmpty()) {
                process(event, userList);
            }
        }

    }

    private void process(Event event, Set<User> userList) {
        UUID historyId = UUID.randomUUID();
        // TODO: fetching the endpoint here is just a temporary workaround to avoid "Deferred enlistment not supported" when saving the history
        Endpoint endpoint = endpointRepository.getOrCreateDefaultSystemSubscription(event.getAccountId(), event.getOrgId(), EndpointType.DRAWER);
        Log.infof("Processing drawer notification [orgId=%s, eventId=%s, historyId=%s]",
            event.getOrgId(), event.getId(), historyId);

        long startTime = System.currentTimeMillis();

        NotificationHistory history = null;
        try {
            String userNameListAsStr = userList.stream().map(usr -> usr.getUsername()).collect(Collectors.joining(","));
            List<DrawerNotification> drawerNotifications = drawerNotificationRepository.create(event, userNameListAsStr);

            // build event thought qute template
            String renderedData = buildNotificationMessage(event);
            // store it on event table
            event.setRenderedDrawerNotification(renderedData);
            eventRepository.updateDrawerNotification(event);

            for (DrawerNotification drawer : drawerNotifications) {
                JsonObject payload = buildJsonPayload(drawer, event);
                sendIt(payload);
            }

            endpoint = endpointRepository.getOrCreateDefaultSystemSubscription(event.getAccountId(), event.getOrgId(), EndpointType.DRAWER);
            history = getHistoryStub(endpoint, event, 0L, historyId);
            history.setStatus(NotificationStatus.SUCCESS);
        } catch (Exception e) {
            endpoint = endpointRepository.getOrCreateDefaultSystemSubscription(event.getAccountId(), event.getOrgId(), EndpointType.DRAWER);
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

    private JsonObject buildJsonPayload(DrawerNotification notif, Event event) {
        DrawerEntry drawerEntry = new DrawerEntry();
        drawerEntry.setId(notif.getId());
        drawerEntry.setDescription(event.getRenderedDrawerNotification());
        drawerEntry.setTitle(event.getEventTypeDisplayName());
        drawerEntry.setRead(notif.isRead());
        drawerEntry.setOrganizations(List.of(notif.getOrgId()));
        drawerEntry.setUsers(List.of(notif.getUserId()));
        drawerEntry.setSource(String.format("%s - %s", event.getApplicationDisplayName(), event.getBundleDisplayName()));
        return JsonObject.mapFrom(drawerEntry);
    }

    private void sendIt(JsonObject payload) {
        CloudEventMetadata<String> cloudEventMetadata = OutgoingCloudEventMetadata.<String>builder()
            .withId(UUID.randomUUID().toString())
            .withType(CLOUD_EVENT_TYPE_PREFIX)
            .withDataContentType(MediaType.APPLICATION_JSON)
            .withSpecVersion("1.0.2")
            .build();
        TracingMetadata tracingMetadata = TracingMetadata.withPrevious(Context.current());
        Message<String> message = Message.of(payload.encode())
            .addMetadata(cloudEventMetadata)
            .addMetadata(tracingMetadata);
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

        String message = getTemplate()
            .data("data", dataAsMap)
            .render();

        return message;
    }

    @CacheResult(cacheName = "drawer-template")
    TemplateInstance getTemplate() {
        IntegrationTemplate integrationTemplate = templateRepository.findIntegrationTemplate(null, null, DEFAULT, "drawer")
            .orElseThrow(() -> new IllegalStateException("No default template defined for drawer"));
        String template = integrationTemplate.getTheTemplate().getData();
        return templateService.compileTemplate(template, integrationTemplate.getTheTemplate().getName());
    }
}
