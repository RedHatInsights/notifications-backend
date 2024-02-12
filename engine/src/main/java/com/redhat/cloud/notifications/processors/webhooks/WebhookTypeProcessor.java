package com.redhat.cloud.notifications.processors.webhooks;

import com.redhat.cloud.notifications.DelayedThrower;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

import static com.redhat.cloud.notifications.events.EndpointProcessor.DELAYED_EXCEPTION_MSG;
import static com.redhat.cloud.notifications.processors.AuthenticationType.BASIC;
import static com.redhat.cloud.notifications.processors.AuthenticationType.BEARER;
import static com.redhat.cloud.notifications.processors.AuthenticationType.SECRET_TOKEN;

@ApplicationScoped
public class WebhookTypeProcessor extends EndpointTypeProcessor {

    public static final String PROCESSED_WEBHOOK_COUNTER = "processor.webhook.processed";

    @Inject
    BaseTransformer transformer;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    MeterRegistry registry;

    private Counter processedWebhookCount;

    @Inject
    ConnectorSender connectorSender;

    @PostConstruct
    void postConstruct() {
        processedWebhookCount = registry.counter(PROCESSED_WEBHOOK_COUNTER);
    }

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
        processedWebhookCount.increment();

        WebhookProperties properties = endpoint.getProperties(WebhookProperties.class);

        final JsonObject payload = transformer.toJsonObject(event);

        final JsonObject connectorData = new JsonObject();

        // TODO RHCLOUD-24930 Stop sending all properties while only "method", "url" and "disable_ssl_verification" are needed in the connector.
        connectorData.put("endpoint_properties", JsonObject.mapFrom(properties));
        connectorData.put("payload", payload);

        getAuthentication(properties).ifPresent(authentication -> {
            connectorData.put("authentication", authentication);
        });

        connectorSender.send(event, endpoint, connectorData);
    }

    private static Optional<JsonObject> getAuthentication(WebhookProperties properties) {
        if (properties.getBasicAuthenticationSourcesId() != null) {
            return Optional.of(JsonObject.of(
                "type", BASIC,
                "secretId", properties.getBasicAuthenticationSourcesId()
            ));
        } else if (properties.getBearerAuthenticationSourcesId() != null) {
            return Optional.of(JsonObject.of(
                "type", BEARER,
                "secretId", properties.getBearerAuthenticationSourcesId()
            ));
        } else if (properties.getSecretTokenSourcesId() != null) {
            return Optional.of(JsonObject.of(
                "type", SECRET_TOKEN,
                "secretId", properties.getSecretTokenSourcesId()
            ));
        } else {
            return Optional.empty();
        }
    }
}
