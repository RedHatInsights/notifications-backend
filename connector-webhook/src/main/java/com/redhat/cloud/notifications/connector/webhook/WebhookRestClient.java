package com.redhat.cloud.notifications.connector.webhook;

import io.quarkus.rest.client.reactive.Url;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import static com.redhat.cloud.notifications.connector.webhook.WebhookMessageHandler.JSON_UTF8;
import static com.redhat.cloud.notifications.connector.webhook.WebhookMessageHandler.X_INSIGHT_TOKEN_HEADER;

@RegisterRestClient(configKey = "connector-rest-client")
public interface WebhookRestClient {


    @POST
    @Consumes(JSON_UTF8)
    Response post(@HeaderParam(X_INSIGHT_TOKEN_HEADER) String xRhSourcesOrgId,
                  @HeaderParam("Authorization") String bearer,
                  @Url String url,
                  String body);
}
