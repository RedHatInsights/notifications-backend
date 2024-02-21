package com.redhat.cloud.notifications.connector.authentication.secrets;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_ID;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_PASSWORD;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_USERNAME;

@ApplicationScoped
public class SecretsLoader implements Processor {

    private static final String SOURCES_API_PSK = "notifications.connector.authentication.secrets-loader.sources-api-psk";
    private static final String SOURCES_TIMER = "sources.get.secret.request";

    @ConfigProperty(name = SOURCES_API_PSK, defaultValue = "development-value-123")
    String sourcesApiPsk;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    @RestClient
    SourcesClient sourcesClient;

    @Override
    public void process(Exchange exchange) {
        Long secretId = exchange.getProperty(SECRET_ID, Long.class);
        if (secretId != null) {
            String orgId = exchange.getProperty(ORG_ID, String.class);

            Log.debugf("Calling Sources to retrieve a secret [orgId=%s, secretId=%d]", orgId, secretId);

            Timer.Sample timer = Timer.start(meterRegistry);
            SourcesSecret sourcesSecret = sourcesClient.getById(orgId, sourcesApiPsk, secretId);
            timer.stop(meterRegistry.timer(SOURCES_TIMER));

            if (sourcesSecret.username != null && !sourcesSecret.username.isBlank()) {
                Log.debug("Found a secret username in the response from Sources");
                exchange.setProperty(SECRET_USERNAME, sourcesSecret.username);
            }

            if (sourcesSecret.password != null && !sourcesSecret.password.isBlank()) {
                Log.debug("Found a secret password in the response from Sources");
                exchange.setProperty(SECRET_PASSWORD, sourcesSecret.password);
            }
        }
    }
}
