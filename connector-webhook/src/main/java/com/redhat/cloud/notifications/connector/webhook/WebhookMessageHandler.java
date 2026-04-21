package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationLoader;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationResult;
import com.redhat.cloud.notifications.connector.v2.MessageHandler;
import com.redhat.cloud.notifications.connector.v2.http.HttpNotificationValidator;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpMessageDetails;
import com.redhat.cloud.notifications.connector.v2.http.models.NotificationToConnectorHttp;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.util.Optional;

import static com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationType.BEARER;
import static com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationType.SECRET_TOKEN;

@ApplicationScoped
public class WebhookMessageHandler extends MessageHandler {

    public static final String X_INSIGHT_TOKEN_HEADER = "X-Insight-Token";

    @Inject
    AuthenticationLoader authenticationLoader;

    @Inject
    @RestClient
    WebhookRestClient webhookRestClient;

    @Inject
    HttpNotificationValidator httpNotificationValidator;

    @Override
    public HandledMessageDetails handle(final IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {

        // Parse and validate the incoming notification
        NotificationToConnectorHttp notification = httpNotificationValidator.parseAndValidate(incomingCloudEvent);

        final Optional<AuthenticationResult> authenticationResultOptional;
        try {
            authenticationResultOptional = authenticationLoader.fetchAuthenticationData(notification.getOrgId(), notification.getAuthentication());
        } catch (WebApplicationException | IllegalStateException e) {
            Log.errorf(e, "Failed to fetch authentication data [orgId=%s]", notification.getOrgId());
            throw new RuntimeException("Error fetching authentication data", e);
        }

        HandledHttpMessageDetails handledMessageDetails = new HandledHttpMessageDetails();
        handledMessageDetails.targetUrl = notification.getEndpointProperties().getTargetUrl();

        final String encodedPayload = notification.getPayload().encode();
        if (authenticationResultOptional.isPresent()) {
            if (BEARER == authenticationResultOptional.get().authenticationType) {
                final String bearerToken = "Bearer " + authenticationResultOptional.get().password;
                try (Response response = webhookRestClient.postWithBearer(bearerToken, notification.getEndpointProperties().getTargetUrl(), encodedPayload)) {
                    handledMessageDetails.httpStatus = response.getStatus();
                }
            } else if (SECRET_TOKEN == authenticationResultOptional.get().authenticationType) {
                final String insightToken = authenticationResultOptional.get().password;
                try (Response response = webhookRestClient.postWithInsightToken(insightToken, notification.getEndpointProperties().getTargetUrl(), encodedPayload)) {
                    handledMessageDetails.httpStatus = response.getStatus();
                }
            } else {
                throw new RuntimeException("Unsupported authentication type: " + authenticationResultOptional.get().authenticationType);
            }
        } else {
            try (Response response = webhookRestClient.post(notification.getEndpointProperties().getTargetUrl(), encodedPayload)) {
                handledMessageDetails.httpStatus = response.getStatus();
            }
        }

        return handledMessageDetails;
    }
}
