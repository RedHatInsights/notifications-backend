package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.drawer.config.DrawerConnectorConfig;
import com.redhat.cloud.notifications.connector.drawer.models.DrawerNotificationToConnector;
import com.redhat.cloud.notifications.connector.drawer.models.DrawerUser;
import com.redhat.cloud.notifications.connector.drawer.models.HandledDrawerMessageDetails;
import com.redhat.cloud.notifications.connector.drawer.recipients.recipientsresolver.ExternalRecipientsResolver;
import com.redhat.cloud.notifications.connector.v2.MessageHandler;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@ApplicationScoped
public class DrawerMessageHandler extends MessageHandler {

    static final String RECIPIENTS_RESOLVER_RESPONSE_TIME_METRIC = "email.recipients_resolver.response.time";

    public static final String DRAWER_CHANNEL = "drawer";

    @Inject
    ExternalRecipientsResolver externalRecipientsResolver;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    @Channel(DRAWER_CHANNEL)
    Emitter<JsonObject> emitter;

    @Inject
    TemplateService templateService;

    @Inject
    DrawerConnectorConfig drawerConnectorConfig;

    @Override
    public HandledMessageDetails handle(final IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        DrawerNotificationToConnector notification = incomingCloudEvent.getData().mapTo(DrawerNotificationToConnector.class);

        // fetch recipients
        Set<String> recipientsList = fetchRecipients(notification);

        if (recipientsList.isEmpty()) {
            Log.infof("Skipped Email notification because the recipients list was empty [orgId=$%s, historyId=%s]",
                notification.getOrgId(),
                incomingCloudEvent.getId());
        } else {
            if (drawerConnectorConfig.useCommonTemplateModule()) {
                final Map<String, Object> eventDataMap = notification.getEventData();

                String templatedEvent = null;
                if (eventDataMap != null) {
                    TemplateDefinition templateDefinition = new TemplateDefinition(
                        IntegrationType.DRAWER,
                        eventDataMap.get("bundle").toString(),
                        eventDataMap.get("application").toString(),
                        eventDataMap.get("event_type").toString());
                    templatedEvent = templateService.renderTemplate(templateDefinition, eventDataMap);
                }

                if (!notification.getDrawerEntryPayload().getDescription().equals(templatedEvent)) {
                    Log.errorf("Legacy and new rendered messages are different: '%s' vs. '%s'", notification.getDrawerEntryPayload().getDescription(), templatedEvent);
                } else {
                    Log.debugf("Legacy and new rendered messages are identical");
                }
            }
            Message<JsonObject> builtKafkaMessage = DrawerPayloadBuilder.buildDrawerMessage(notification.getDrawerEntryPayload(), recipientsList);
            if (drawerConnectorConfig.pushNotificationsToKafka()) {
                emitter.send(builtKafkaMessage);
            }
        }

        return new HandledDrawerMessageDetails(recipientsList);
    }

    private Set<String> fetchRecipients(DrawerNotificationToConnector drawerNotification) {

        boolean subscribedByDefault = true;
        final Timer.Sample recipientsResolverResponseTimeMetric = Timer.start(meterRegistry);
        Set<String> recipientsList = externalRecipientsResolver.recipientUsers(
                drawerNotification.getOrgId(),
                drawerNotification.getRecipientSettings(),
                drawerNotification.getUnsubscribers(),
                subscribedByDefault,
                drawerNotification.getAuthorizationCriteria())
            .stream().map(DrawerUser::getUsername).filter(username -> username != null && !username.isBlank()).collect(toSet());
        recipientsResolverResponseTimeMetric.stop(meterRegistry.timer(RECIPIENTS_RESOLVER_RESPONSE_TIME_METRIC));

        return recipientsList;
    }
}
