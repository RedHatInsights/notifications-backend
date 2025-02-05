package com.redhat.cloud.notifications.processors.pagerduty;

import com.redhat.cloud.notifications.DelayedThrower;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.PagerDutyProperties;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.InsightsUrlsBuilder;
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
import static com.redhat.cloud.notifications.processors.AuthenticationType.SECRET_TOKEN;
import static com.redhat.cloud.notifications.transformers.BaseTransformer.PAYLOAD;

@ApplicationScoped
public class PagerDutyProcessor extends EndpointTypeProcessor {

    public static final String PROCESSED_PAGERDUTY_COUNTER = "processor.pagerduty.processed";
    public static final String INSIGHTS_URL_FROM_PAGERDUTY = "pagerduty";

    @Inject
    BaseTransformer transformer;

    @Inject
    EngineConfig engineConfig;

    @Inject
    InsightsUrlsBuilder insightsUrlsBuilder;

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

    private void process(Event event, Endpoint endpoint) {
        processedPagerDutyCounter.increment();
        PagerDutyProperties properties = endpoint.getProperties(PagerDutyProperties.class);

        JsonObject connectorData = new JsonObject();
        JsonObject transformedEvent = transformer.toJsonObject(event);
        insightsUrlsBuilder.buildInventoryUrl(transformedEvent, INSIGHTS_URL_FROM_PAGERDUTY).ifPresent(url -> transformedEvent.put("inventory_url", url));
        transformedEvent.put("application_url", insightsUrlsBuilder.buildApplicationUrl(transformedEvent, INSIGHTS_URL_FROM_PAGERDUTY));
        transformedEvent.put("severity", properties.getSeverity());

        connectorData.put(PAYLOAD, transformedEvent);

        if (properties.getSecretTokenSourcesId() != null) {
            JsonObject authentication = JsonObject.of(
                    "type", SECRET_TOKEN,
                    "secretId", properties.getSecretTokenSourcesId()
            );
            connectorData.put("authentication", authentication);
        }

        connectorSender.send(event, endpoint, connectorData);
    }
}
