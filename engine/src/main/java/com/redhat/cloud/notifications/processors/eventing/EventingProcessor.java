package com.redhat.cloud.notifications.processors.eventing;

import com.redhat.cloud.notifications.Base64Utils;
import com.redhat.cloud.notifications.DelayedThrower;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.routers.sources.SecretUtils;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.redhat.cloud.notifications.events.EndpointProcessor.DELAYED_EXCEPTION_MSG;
import static com.redhat.cloud.notifications.models.NotificationHistory.getHistoryStub;
import static com.redhat.cloud.notifications.models.NotificationStatus.FAILED_INTERNAL;
import static com.redhat.cloud.notifications.models.NotificationStatus.PROCESSING;

@ApplicationScoped
public class EventingProcessor extends EndpointTypeProcessor {

    public static final String PROCESSED_COUNTER_NAME = "processor.camel.processed";
    public static final String TOKEN_HEADER = "X-Insight-Token";
    public static final String NOTIF_METADATA_KEY = "notif-metadata";

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    MeterRegistry registry;

    @Inject
    SecretUtils secretUtils;

    @Inject
    ConnectorSender connectorSender;

    @Inject
    NotificationHistoryRepository notificationHistoryRepository;

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

        Log.infof("Sending CloudEvent [orgId=%s, integration=%s, historyId=%s, eventId=%s]",
                endpoint.getOrgId(), endpoint.getName(), historyId, event.getId());

        NotificationHistory history = getHistoryStub(endpoint, event, 0L, historyId);
        history.setStatus(PROCESSING);
        persistNotificationHistory(history);

        JsonObject payload = buildPayload(event, endpoint);

        try {
            connectorSender.send(payload, historyId, endpoint.getSubType());
        } catch (Exception e) {
            history.setStatus(FAILED_INTERNAL);
            history.setDetails(Map.of("failure", e.getMessage()));
            notificationHistoryRepository.updateHistoryItem(history);
            Log.infof(e, "Sending CloudEvent failed [orgId=%s, integration=%s, historyId=%s, eventId=%s]",
                    endpoint.getOrgId(), endpoint.getName(), historyId, event.getId());
        }
    }

    private JsonObject buildPayload(Event event, Endpoint endpoint) {
        CamelProperties properties = endpoint.getProperties(CamelProperties.class);

        JsonObject metaData = new JsonObject();
        metaData.put("trustAll", String.valueOf(properties.getDisableSslVerification()));
        metaData.put("url", properties.getUrl());
        metaData.put("type", endpoint.getSubType());

        if (featureFlipper.isSourcesUsedAsSecretsBackend()) {
            // Get the basic authentication and secret token secrets from Sources.
            secretUtils.loadSecretsForEndpoint(endpoint);
        }
        getSecretToken(properties).ifPresent(secretToken -> metaData.put(TOKEN_HEADER, secretToken));
        getBasicAuth(properties).ifPresent(basicAuth -> metaData.put("basicAuth", basicAuth));

        final JsonObject payload = baseTransformer.toJsonObject(event);
        payload.put(NOTIF_METADATA_KEY, metaData);

        return payload;
    }

    private static Optional<String> getSecretToken(CamelProperties properties) {
        if (properties.getSecretToken() == null || properties.getSecretToken().isBlank()) {
            return Optional.empty();
        } else {
            return Optional.of(properties.getSecretToken());
        }
    }

    private static Optional<String> getBasicAuth(CamelProperties properties) {
        BasicAuthentication basicAuthentication = properties.getBasicAuthentication();
        if (basicAuthentication == null || basicAuthentication.getUsername() == null || basicAuthentication.getPassword() == null) {
            return Optional.empty();
        } else {
            String credentials = basicAuthentication.getUsername() + ":" + basicAuthentication.getPassword();
            return Optional.of(Base64Utils.encode(credentials));
        }
    }
}
