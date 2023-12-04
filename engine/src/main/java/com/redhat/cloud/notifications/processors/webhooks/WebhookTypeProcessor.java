package com.redhat.cloud.notifications.processors.webhooks;

import com.redhat.cloud.notifications.DelayedThrower;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.routers.sources.SecretUtils;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

import static com.redhat.cloud.notifications.events.EndpointProcessor.DELAYED_EXCEPTION_MSG;

@ApplicationScoped
public class WebhookTypeProcessor extends EndpointTypeProcessor {

    public static final String PROCESSED_WEBHOOK_COUNTER = "processor.webhook.processed";

    @Inject
    BaseTransformer transformer;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    MeterRegistry registry;

    @Inject
    SecretUtils secretUtils;

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

        /*
         * Get the basic authentication and secret token secrets from Sources.
         */
        if (this.featureFlipper.isSourcesUsedAsSecretsBackend()) {
            this.secretUtils.loadSecretsForEndpoint(endpoint);
        }

        final JsonObject connectorData = new JsonObject();

        connectorData.put("endpoint_properties", JsonObject.mapFrom(properties));
        connectorData.put("payload", payload);

        connectorSender.send(event, endpoint, connectorData);
    }
}
