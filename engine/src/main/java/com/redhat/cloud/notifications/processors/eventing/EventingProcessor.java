package com.redhat.cloud.notifications.processors.eventing;

import com.redhat.cloud.notifications.Base64Utils;
import com.redhat.cloud.notifications.DelayedThrower;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.routers.sources.SecretUtils;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

import static com.redhat.cloud.notifications.events.EndpointProcessor.DELAYED_EXCEPTION_MSG;
import static com.redhat.cloud.notifications.processors.AuthenticationType.SECRET_TOKEN;

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

        setLegacyAuthData(endpoint, properties, metaData);
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

    // TODO RHCLOUD-24930 Remove this method after the migration is done.
    @Deprecated(forRemoval = true)
    private void setLegacyAuthData(Endpoint endpoint, CamelProperties properties, JsonObject metaData) {
        if (featureFlipper.isSourcesUsedAsSecretsBackend()) {
            // Get the basic authentication and secret token secrets from Sources.
            secretUtils.loadSecretsForEndpoint(endpoint);
        }
        getSecretToken(properties).ifPresent(secretToken -> metaData.put(TOKEN_HEADER, secretToken));
        getBasicAuth(properties).ifPresent(basicAuth -> metaData.put("basicAuth", basicAuth));
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
