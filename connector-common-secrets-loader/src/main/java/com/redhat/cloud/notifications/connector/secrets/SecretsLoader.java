package com.redhat.cloud.notifications.connector.secrets;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.quarkus.runtime.configuration.ProfileManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.secrets.SecretsExchangeProperty.SECRET_ID;
import static com.redhat.cloud.notifications.connector.secrets.SecretsExchangeProperty.SECRET_PASSWORD;
import static com.redhat.cloud.notifications.connector.secrets.SecretsExchangeProperty.SECRET_USERNAME;
import static io.quarkus.runtime.LaunchMode.TEST;

@ApplicationScoped
public class SecretsLoader implements Processor {

    private static final String SECRETS_LOADER_ENABLED = "notifications.connector.secrets-loader.enabled";
    private static final String SOURCES_API_PSK = "notifications.connector.secrets-loader.sources-api-psk";
    private static final String SOURCES_TIMER = "sources.get.secret.request";

    @ConfigProperty(name = SECRETS_LOADER_ENABLED, defaultValue = "false")
    boolean secretsLoaderEnabled;

    @ConfigProperty(name = SOURCES_API_PSK, defaultValue = "development-value-123")
    String sourcesApiPsk;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    @RestClient
    SourcesClient sourcesClient;

    @Override
    public void process(Exchange exchange) {
        if (secretsLoaderEnabled) {
            Long secretId = exchange.getProperty(SECRET_ID, Long.class);
            if (secretId != null) {
                String orgId = exchange.getProperty(ORG_ID, String.class);

                // TODO Lower the log level after the testing phase.
                Log.infof("Calling Sources to retrieve a secret [orgId=%s, secretId=%d]", orgId, secretId);

                Timer.Sample timer = Timer.start(meterRegistry);
                Secret secret = sourcesClient.getById(orgId, sourcesApiPsk, secretId);
                timer.stop(meterRegistry.timer(SOURCES_TIMER));

                if (secret.username != null && !secret.username.isBlank()) {
                    // TODO Lower the log level after the testing phase.
                    Log.info("Found a secret username in the response from Sources");
                    exchange.setProperty(SECRET_USERNAME, secret.username);
                }

                if (secret.password != null && !secret.password.isBlank()) {
                    // TODO Lower the log level after the testing phase.
                    Log.info("Found a secret password in the response from Sources");
                    exchange.setProperty(SECRET_PASSWORD, secret.password);
                }
            }
        }
    }

    // TODO RHCLOUD-24930 Remove this method after the migration is done.
    public void setEnabled(boolean enabled) {
        if (ProfileManager.getLaunchMode() == TEST) {
            secretsLoaderEnabled = enabled;
        }
    }
}
