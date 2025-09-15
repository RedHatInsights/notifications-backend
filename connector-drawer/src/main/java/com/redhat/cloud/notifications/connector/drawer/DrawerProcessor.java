package com.redhat.cloud.notifications.connector.drawer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.ConnectorProcessor;
import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import com.redhat.cloud.notifications.connector.drawer.config.DrawerConnectorConfig;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerEntry;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerEntryPayload;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerNotificationToConnector;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerUser;
import com.redhat.cloud.notifications.connector.drawer.recipients.pojo.RecipientsQuery;
import com.redhat.cloud.notifications.connector.drawer.recipients.recipientsresolver.RecipientsResolverService;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Drawer connector processor that extends the new ConnectorProcessor base class.
 * This replaces the old Camel-based DrawerRouteBuilder.
 */
@ApplicationScoped
public class DrawerProcessor extends ConnectorProcessor {

    public static final String DRAWER_CHANNEL = "drawer";

    @Inject
    DrawerConnectorConfig drawerConfig;

    @Inject
    @RestClient
    RecipientsResolverService recipientsResolverService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @Channel(DRAWER_CHANNEL)
    MutinyEmitter<String> drawerEmitter;

    @Override
    protected Uni<ConnectorResult> processCloudEvent(ExceptionProcessor.ProcessingContext context) {
        return Uni.createFrom().item(() -> {
            try {
                JsonObject cloudEvent = context.getOriginalCloudEvent();
                DrawerNotificationToConnector drawerNotification = objectMapper.readValue(
                    cloudEvent.encode(),
                    DrawerNotificationToConnector.class
                );

                Set<DrawerUser> recipients = resolveRecipients(drawerNotification);
                createAndPublishDrawerEntry(context, drawerNotification, recipients);

                return new ConnectorResult(
                    true,
                    String.format("Drawer event %s processed successfully for %d recipients",
                            context.getId(), recipients.size()),
                    context.getId(),
                    context.getOrgId(),
                    context.getOriginalCloudEvent()
                );

            } catch (Exception e) {
                Log.errorf(e, "Failed to process drawer event %s", context.getId());
                throw new RuntimeException("Failed to process drawer event: " + e.getMessage(), e);
            }
        });
    }

    private Set<DrawerUser> resolveRecipients(DrawerNotificationToConnector drawerNotification) {
        try {
            RecipientsQuery query = new RecipientsQuery();
            query.orgId = drawerNotification.orgId();
            query.recipientSettings = new HashSet<>(drawerNotification.recipientSettings());
            query.unsubscribers = new HashSet<>(drawerNotification.unsubscribers());
            query.authorizationCriteria = drawerNotification.authorizationCriteria();

            return recipientsResolverService.getRecipients(query);
        } catch (Exception e) {
            Log.errorf(e, "Failed to resolve recipients for orgId %s", drawerNotification.orgId());
            throw new RuntimeException("Failed to resolve recipients", e);
        }
    }

    private void createAndPublishDrawerEntry(ExceptionProcessor.ProcessingContext context,
                                             DrawerNotificationToConnector drawerNotification,
                                             Set<DrawerUser> recipients) {
        try {
            Set<String> usernames = recipients.stream()
                    .map(DrawerUser::getUsername)
                    .collect(Collectors.toSet());

            DrawerEntry drawerEntry = new DrawerEntry();
            drawerEntry.setUsernames(usernames);

            DrawerEntryPayload payload = drawerNotification.drawerEntryPayload();
            if (payload.getCreated() == null) {
                payload.setCreated(LocalDateTime.now());
            }
            drawerEntry.setPayload(payload);

            JsonObject drawerEntryJson = JsonObject.mapFrom(drawerEntry);

            Log.infof("Publishing drawer entry for event %s to %d recipients",
                context.getId(), usernames.size());

            drawerEmitter.send(Message.of(drawerEntryJson.encode()));
        } catch (Exception e) {
            Log.errorf(e, "Failed to publish drawer entry for event %s", context.getId());
            throw new RuntimeException("Failed to publish drawer entry", e);
        }
    }
}
