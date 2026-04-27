package com.redhat.cloud.notifications.connector.servicenow;

import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationLoader;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationResult;
import com.redhat.cloud.notifications.connector.v2.MessageHandler;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpMessageDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationType.SECRET_TOKEN;
import static com.redhat.cloud.notifications.connector.servicenow.ServiceNowNotification.URL_KEY;
import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
public class ServiceNowMessageHandler extends MessageHandler {

    public static final String JSON_UTF8 = "application/json; charset=utf-8";
    static final String NOTIF_METADATA = "notif-metadata";
    static final String AUTHENTICATION_KEY = "authentication";
    static final String USERNAME = "rh_insights_integration";

    @Inject
    AuthenticationLoader authenticationLoader;

    @Inject
    @RestClient
    ServiceNowRestClient serviceNowRestClient;

    @Inject
    Validator validator;

    @Override
    public HandledMessageDetails handle(final IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {

        final ServiceNowNotification notification = getAndValidateNotification(incomingCloudEvent);

        final Optional<String> authorizationHeader = fetchOptionalAuthorizationHeader(notification, incomingCloudEvent);

        final String payload = buildPayload(incomingCloudEvent);

        HandledHttpMessageDetails handledMessageDetails = new HandledHttpMessageDetails();
        handledMessageDetails.targetUrl = notification.getTargetUrl();

        try (Response response = authorizationHeader.isPresent()
                ? serviceNowRestClient.postWithBasicAuth(authorizationHeader.get(), notification.getTargetUrl(), payload)
                : serviceNowRestClient.post(notification.getTargetUrl(), payload)) {
            handledMessageDetails.httpStatus = response.getStatus();
        }

        Log.infof("Delivered event %s (orgId %s account %s) to %s",
            incomingCloudEvent.getId(), notification.getOrgId(), notification.accountId, notification.getTargetUrl());

        return handledMessageDetails;
    }

    private Optional<String> fetchOptionalAuthorizationHeader(ServiceNowNotification notification, IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        JsonObject authentication = notification.metadata.getJsonObject(AUTHENTICATION_KEY);
        if (authentication == null) {
            return Optional.empty();
        }

        final Optional<AuthenticationResult> authResult;
        try {
            authResult = authenticationLoader.fetchAuthenticationData(notification.getOrgId(), authentication);
        } catch (WebApplicationException | IllegalStateException e) {
            Log.errorf(e, "Failed to fetch authentication data [orgId=%s, historyId=%s]", notification.getOrgId(), incomingCloudEvent.getId());
            throw new RuntimeException("Error fetching authentication data", e);
        }

        if (authResult.isEmpty()) {
            return Optional.empty();
        }

        AuthenticationResult auth = authResult.get();
        if (auth.authenticationType != SECRET_TOKEN) {
            throw new IllegalStateException("Unsupported authentication type: " + auth.authenticationType);
        }
        return Optional.of("Basic " + Base64.getEncoder().encodeToString(
            (USERNAME + ":" + auth.password).getBytes(UTF_8)));
    }

    private static String buildPayload(IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        final JsonObject data = incomingCloudEvent.getData();
        final JsonObject payloadData = new JsonObject();
        data.forEach(entry -> {
            if (!NOTIF_METADATA.equals(entry.getKey())) {
                payloadData.put(entry.getKey(), entry.getValue());
            }
        });
        return payloadData.encode();
    }

    private ServiceNowNotification getAndValidateNotification(final IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        final ServiceNowNotification notification = incomingCloudEvent.getData().mapTo(ServiceNowNotification.class);
        Set<ConstraintViolation<ServiceNowNotification>> violations = validator.validate(notification);
        if (!violations.isEmpty()) {
            String errorMessage = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
            throw new ConstraintViolationException(
                String.format("Validation failed [historyId=%s]: %s", incomingCloudEvent.getId(), errorMessage),
                violations);
        }

        validateTargetUrl(notification, incomingCloudEvent.getId());

        return notification;
    }

    static void validateTargetUrl(ServiceNowNotification notification, String historyId) {
        final String targetUrl = notification.metadata.getString(URL_KEY);

        if (targetUrl == null || targetUrl.isEmpty()) {
            throw new IllegalArgumentException(
                String.format("Missing or empty 'url' in notification metadata [orgId=%s, historyId=%s]",
                    notification.getOrgId(), historyId));
        }

        try {
            URI uri = new URI(targetUrl);
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Illegal URL scheme: " + scheme + ". Only https is allowed.");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid target URL: " + targetUrl, e);
        }
    }
}
