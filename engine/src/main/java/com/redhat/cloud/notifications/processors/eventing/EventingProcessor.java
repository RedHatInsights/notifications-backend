package com.redhat.cloud.notifications.processors.eventing;

import com.redhat.cloud.notifications.DelayedThrower;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

import static com.redhat.cloud.notifications.events.EndpointProcessor.DELAYED_EXCEPTION_MSG;
import static com.redhat.cloud.notifications.processors.AuthenticationType.SECRET_TOKEN;

@ApplicationScoped
public class EventingProcessor extends EndpointTypeProcessor {

    public static final String PROCESSED_COUNTER_NAME = "processor.camel.processed";
    public static final String NOTIF_METADATA_KEY = "notif-metadata";

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    MeterRegistry registry;

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
        registry.counter(PROCESSED_COUNTER_NAME, "subType", endpoint.getSubType()).increment();

        JsonObject payload = buildPayload(event, endpoint);

        connectorSender.send(event, endpoint, payload);
    }

    private JsonObject buildPayload(Event event, Endpoint endpoint) {
        CamelProperties properties = endpoint.getProperties(CamelProperties.class);

        JsonObject metaData = new JsonObject();
        metaData.put("trustAll", String.valueOf(properties.getDisableSslVerification()));
        metaData.put("url", properties.getUrl());
        metaData.put("type", endpoint.getSubType());

        if (properties.getSecretTokenSourcesId() != null) {
            JsonObject authentication = JsonObject.of(
                "type", SECRET_TOKEN,
                "secretId", properties.getSecretTokenSourcesId()
            );
            metaData.put("authentication", authentication);
        }

        final JsonObject payload = baseTransformer.toJsonObject(event);
        payload.put(NOTIF_METADATA_KEY, metaData);

        return payload;
    }
}
