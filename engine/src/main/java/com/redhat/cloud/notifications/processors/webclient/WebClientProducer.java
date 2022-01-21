package com.redhat.cloud.notifications.processors.webclient;

import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@ApplicationScoped
public class WebClientProducer {

    private static final Logger LOGGER = Logger.getLogger(WebClientProducer.class);

    @Inject
    Vertx vertx;

    @ConfigProperty(name = "webclient.max_pool_size")
    Optional<Integer> maxPoolSize;

    @Produces
    @Singleton
    @SslVerificationEnabled
    public WebClient securedWebClient() {
        return WebClient.create(vertx, buildOptions(false));
    }

    @Produces
    @Singleton
    @SslVerificationDisabled
    public WebClient unsecuredWebClient() {
        return WebClient.create(vertx, buildOptions(true));
    }

    @Produces
    @Singleton
    @BopWebClient
    public WebClient bopWebClient() {
        return WebClient.create(vertx, buildOptions(true));
    }

    private WebClientOptions buildOptions(boolean trustAll) {
        WebClientOptions options = new WebClientOptions()
                .setTrustAll(trustAll)
                .setConnectTimeout(3000); // TODO Should this be configurable by the system? We need a maximum in any case
        if (maxPoolSize.isPresent()) {
            LOGGER.debugf("Producing a WebClient with a configured max pool size: %d", maxPoolSize.get());
            options = options.setMaxPoolSize(maxPoolSize.get());
        }
        return options;
    }
}
