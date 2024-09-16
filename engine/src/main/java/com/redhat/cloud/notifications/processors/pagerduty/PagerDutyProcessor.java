package com.redhat.cloud.notifications.processors.pagerduty;

import com.redhat.cloud.notifications.DelayedThrower;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.PagerDutyProperties;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.transformers.PagerDutyTransformer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

import static com.redhat.cloud.notifications.events.EndpointProcessor.DELAYED_EXCEPTION_MSG;
import static com.redhat.cloud.notifications.processors.AuthenticationType.SECRET_TOKEN;

@ApplicationScoped
public class PagerDutyProcessor extends EndpointTypeProcessor {

    public static final String NOTIF_METADATA_KEY = "notif-metadata";
    public static final String PROCESSED_PAGERDUTY_COUNTER = "processor.pagerduty.processed";

    @Inject
    PagerDutyTransformer transformer;

    @Inject
    EngineConfig engineConfig;

    @Inject
    MeterRegistry registry;

    @Inject
    ConnectorSender connectorSender;

    private Counter processedPagerDutyCounter;

    @PostConstruct
    void postConstruct() {
        processedPagerDutyCounter = registry.counter(PROCESSED_PAGERDUTY_COUNTER);
    }

    @Override
    public void process(Event event, List<Endpoint> endpoints) {
        if (engineConfig.isEmailsOnlyModeEnabled()) {
            Log.warn("@channel Skipping event processing because Notifications is running in emails only mode");
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

    /**
     * Constructs a <a href="https://support.pagerduty.com/main/docs/pd-cef">PD-CEF</a> format alert event.
     *
     * <ul>
     *     <li>TODO determine how event severity is specified (at the endpoint level, or paired with behaviour
     *     groups/workspaces) - see RHCLOUD-33788</li>
     * </ul>
     */
    private void process(Event event, Endpoint endpoint) {
        processedPagerDutyCounter.increment();
        PagerDutyProperties properties = endpoint.getProperties(PagerDutyProperties.class);

        JsonObject metadata = new JsonObject();
        metadata.put("url", properties.getUrl());
        metadata.put("method", properties.getMethod());
        metadata.put("trustAll", properties.getDisableSslVerification());

        if (properties.getSecretTokenSourcesId() != null) {
            JsonObject authentication = JsonObject.of(
                    "type", SECRET_TOKEN,
                    "secretId", properties.getSecretTokenSourcesId()
            );
            metadata.put("authentication", authentication);
        }

        final JsonObject connectorData = transformer.toJsonObject(event);
        connectorData.put(NOTIF_METADATA_KEY, metadata);

        connectorSender.send(event, endpoint, connectorData);
    }
}
