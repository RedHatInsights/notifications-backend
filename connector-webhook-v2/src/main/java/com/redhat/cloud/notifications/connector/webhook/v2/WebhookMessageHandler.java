package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationResult;
import com.redhat.cloud.notifications.connector.authentication.v2.secrets.SecretsLoader;
import com.redhat.cloud.notifications.connector.v2.MessageHandler;
import com.redhat.cloud.notifications.connector.v2.http.HttpClientConfiguration;
import com.redhat.cloud.notifications.connector.v2.http.pojo.NotificationToConnectorHttp;
import com.redhat.cloud.notifications.connector.v2.pojo.HandledMessageDetails;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;

@ApplicationScoped
public class WebhookMessageHandler extends MessageHandler {

    @Inject
    SecretsLoader secretsLoader;

    @Inject
    HttpClientConfiguration httpClientConfiguration;

    @Override
    public HandledMessageDetails handle(final IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        Optional<AuthenticationResult> authenticationResultOptional = secretsLoader.fetchAuthenticationData(incomingCloudEvent);

        NotificationToConnectorHttp notification = incomingCloudEvent.getData().mapTo(NotificationToConnectorHttp.class);

        httpClientConfiguration.createHttpRestClient().post(notification.getTargetUrl(), incomingCloudEvent.getData().encode());

        return new HandledMessageDetails();
    }
}
