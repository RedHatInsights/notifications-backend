package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationLoader;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationResult;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationType;
import com.redhat.cloud.notifications.connector.pagerduty.config.PagerDutyConnectorConfig;
import com.redhat.cloud.notifications.connector.v2.MessageHandler;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpMessageDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class PagerDutyMessageHandler extends MessageHandler {

    @Inject
    AuthenticationLoader authenticationLoader;

    @Inject
    @RestClient
    PagerDutyRestClient pagerDutyRestClient;

    @Inject
    PagerDutyConnectorConfig connectorConfig;

    @Inject
    Validator validator;

    @Override
    public HandledMessageDetails handle(final IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {

        final PagerDutyNotification notification = getAndValidateNotification(incomingCloudEvent);

        final String routingKey = fetchRoutingKey(notification);

        String payload = PagerDutyTransformer.buildPagerDutyPayload(notification.payload, routingKey,
                connectorConfig.isDynamicPagerdutySeverityEnabled(notification.getOrgId()));

        String targetUrl = connectorConfig.getPagerDutyUrl();
        HandledHttpMessageDetails handledMessageDetails = new HandledHttpMessageDetails();
        handledMessageDetails.targetUrl = targetUrl;

        try (Response response = pagerDutyRestClient.post(payload)) {
            handledMessageDetails.httpStatus = response.getStatus();
        }

        return handledMessageDetails;
    }

    private PagerDutyNotification getAndValidateNotification(final IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        final PagerDutyNotification notification = incomingCloudEvent.getData().mapTo(PagerDutyNotification.class);
        Set<ConstraintViolation<PagerDutyNotification>> violations = validator.validate(notification);
        if (!violations.isEmpty()) {
            String errorMessage = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
            throw new ConstraintViolationException("Validation failed: " + errorMessage, violations);
        }

        PagerDutyTransformer.validatePayload(notification.payload, notification.getOrgId());

        return notification;
    }

    private String fetchRoutingKey(PagerDutyNotification notification) {
        AuthenticationResult resolvedAuth = authenticationLoader
                .fetchAuthenticationData(notification.getOrgId(), notification.authentication)
                .orElseThrow(() -> new IllegalStateException("Authentication is required for PagerDuty"));

        if (resolvedAuth.authenticationType != AuthenticationType.SECRET_TOKEN) {
            throw new IllegalStateException("Unsupported authentication type: " + resolvedAuth.authenticationType);
        }

        return resolvedAuth.password;
    }
}
