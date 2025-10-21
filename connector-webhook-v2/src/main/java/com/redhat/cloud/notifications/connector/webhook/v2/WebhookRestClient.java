package com.redhat.cloud.notifications.connector.webhook.v2;

import io.quarkus.rest.client.reactive.Url;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "connector-rest-client")
public interface WebhookRestClient {

    @POST
    Response post(@Url String url, String body);
}
