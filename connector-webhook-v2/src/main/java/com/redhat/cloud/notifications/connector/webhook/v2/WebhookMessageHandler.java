package com.redhat.cloud.notifications.connector.webhook.v2;

import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationLoader;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationResult;
import com.redhat.cloud.notifications.connector.v2.MessageHandler;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpMessageDetails;
import com.redhat.cloud.notifications.connector.v2.http.models.NotificationToConnectorHttp;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.util.Optional;

import static com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationType.BEARER;
import static com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationType.SECRET_TOKEN;

@ApplicationScoped
public class WebhookMessageHandler extends MessageHandler {

    public static final String JSON_UTF8 = "application/json; charset=utf-8";
    public static final String X_INSIGHT_TOKEN_HEADER = "X-Insight-Token";

    @Inject
    AuthenticationLoader secretsLoader;

    @Inject
    @RestClient
    WebhookRestClient webhookRestClient;

    @Override
    public HandledMessageDetails handle(final IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {

        NotificationToConnectorHttp notification = incomingCloudEvent.getData().mapTo(NotificationToConnectorHttp.class);
        final Optional<AuthenticationResult> authenticationResultOptional;
        try {
            authenticationResultOptional = secretsLoader.fetchAuthenticationData(notification.getOrgId(), notification.getAuthentication());
        } catch (Exception e) {
            throw new RuntimeException("Error fetching secrets '" + e.getMessage() + "'", e);
        }

        String bearerToken = null;
        String insightToken = null;
        if (authenticationResultOptional.isPresent()) {
            if (BEARER == authenticationResultOptional.get().authenticationType) {
                bearerToken = authenticationResultOptional.get().password;
            } else if (SECRET_TOKEN == authenticationResultOptional.get().authenticationType) {
                insightToken = authenticationResultOptional.get().password;
            }
        }
        try (Response response = webhookRestClient.post(insightToken, bearerToken, notification.getEndpointProperties().getTargetUrl(), notification.getPayload().encode())) {
            Log.info(response.getStatus());
        }
        HandledHttpMessageDetails handledMessageDetails = new HandledHttpMessageDetails();
        handledMessageDetails.targetUrl = notification.getEndpointProperties().getTargetUrl();
        return handledMessageDetails;
    }
}
