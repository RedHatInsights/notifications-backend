package com.redhat.cloud.notifications.connector.v2.http;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import java.net.URI;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class HttpClientConfiguration {

    @Inject
    HttpConnectorConfig httpConnectorConfig;

    @Produces
    @ApplicationScoped
    public HttpRestClient createHttpRestClient() {
        return QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create("http://localhost"))
                .connectTimeout(httpConnectorConfig.getHttpConnectTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(httpConnectorConfig.getHttpSocketTimeout(), TimeUnit.MILLISECONDS)
                .build(HttpRestClient.class);
    }
}
