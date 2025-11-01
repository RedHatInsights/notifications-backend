package com.redhat.cloud.notifications.connector.v2.http;

import io.quarkus.rest.client.reactive.Url;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "connector-rest-client")
public interface HttpRestClient {

    @POST
    Response post(@Url String url, String body);
}
