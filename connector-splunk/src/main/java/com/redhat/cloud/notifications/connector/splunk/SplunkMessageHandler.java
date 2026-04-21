package com.redhat.cloud.notifications.connector.splunk;

import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationLoader;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationResult;
import com.redhat.cloud.notifications.connector.v2.MessageHandler;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpMessageDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonArray;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class SplunkMessageHandler extends MessageHandler {

    static final String NOTIF_METADATA = "notif-metadata";
    static final String URL_KEY = "url";
    static final String AUTHENTICATION_KEY = "authentication";

    static final String SERVICES_COLLECTOR = "/services/collector";
    static final String EVENT = "/event";
    static final String SERVICES_COLLECTOR_EVENT = SERVICES_COLLECTOR + EVENT;
    static final String RAW = "/raw";
    static final String SERVICES_COLLECTOR_RAW = SERVICES_COLLECTOR + RAW;

    static final String SOURCE_KEY = "source";
    static final String SOURCE_VALUE = "eventing";
    static final String SOURCE_TYPE_KEY = "sourcetype";
    static final String SOURCE_TYPE_VALUE = "Insights event";
    static final String EVENT_KEY = "event";

    @Inject
    AuthenticationLoader authenticationLoader;

    @Inject
    @RestClient
    SplunkRestClient splunkRestClient;

    @Inject
    Validator validator;

    @Override
    public HandledMessageDetails handle(final IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {

        if (incomingCloudEvent.getData() == null) {
            throw new IllegalStateException("CloudEvent data is null");
        }

        final SplunkNotification notification = getAndValidateSplunkNotificationFormat(incomingCloudEvent);

        final String targetUrl = fixTargetUrlPathIfNeeded(notification.metadata.getString(URL_KEY));

        final String authorizationHeader = fetchAndCheckSplunkAuthorizationHeader(notification);

        String payload = buildSplunkPayload(notification);

        HandledHttpMessageDetails handledMessageDetails = new HandledHttpMessageDetails();
        handledMessageDetails.targetUrl = targetUrl;

        try (Response response = splunkRestClient.post(authorizationHeader, targetUrl, payload)) {
            handledMessageDetails.httpStatus = response.getStatus();
        }

        Log.infof("Delivered event %s (orgId %s account %s) to %s",
            incomingCloudEvent.getId(), notification.getOrgId(), notification.accountId, targetUrl);

        return handledMessageDetails;
    }

    private SplunkNotification getAndValidateSplunkNotificationFormat(final IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        final SplunkNotification splunkNotification = incomingCloudEvent.getData().mapTo(SplunkNotification.class);
        Set<ConstraintViolation<SplunkNotification>> violations = validator.validate(splunkNotification);
        if (!violations.isEmpty()) {
            String errorMessage = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
            throw new ConstraintViolationException("Validation failed: " + errorMessage, violations);
        }

        String targetUrl = splunkNotification.metadata.getString(URL_KEY);
        if (targetUrl == null || targetUrl.isEmpty()) {
            throw new IllegalArgumentException("Missing or empty 'url' in notification metadata");
        }
        validateTargetUrl(targetUrl);

        return splunkNotification;
    }

    private String fetchAndCheckSplunkAuthorizationHeader(SplunkNotification notification) {
        JsonObject authentication = notification.metadata.getJsonObject(AUTHENTICATION_KEY);

        final Optional<AuthenticationResult> authResult;
        try {
            authResult = authenticationLoader.fetchAuthenticationData(notification.getOrgId(), authentication);
        } catch (WebApplicationException | IllegalStateException e) {
            Log.errorf(e, "Failed to fetch authentication data [orgId=%s]", notification.getOrgId());
            throw new RuntimeException("Error fetching authentication data", e);
        }

        if (authResult.isEmpty()) {
            throw new IllegalStateException("Authentication is required for Splunk HEC");
        }

        final AuthenticationResult resolvedAuth = authResult.get();
        return switch (resolvedAuth.authenticationType) {
            case SECRET_TOKEN -> {
                if (resolvedAuth.password == null || resolvedAuth.password.isBlank()) {
                    throw new IllegalStateException("Missing Splunk secret token");
                }
                yield "Splunk " + resolvedAuth.password;
            }
            case BEARER -> throw new IllegalStateException("Unsupported authentication type: BEARER");
            default -> throw new IllegalStateException("Unexpected authentication type: " + resolvedAuth.authenticationType);
        };
    }

    static void validateTargetUrl(String targetUrl) {
        try {
            URI uri = new URI(targetUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("Illegal URL scheme: " + uri.getScheme() + ". Only https is allowed.");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid target URL: " + targetUrl, e);
        }
    }

    static String fixTargetUrlPathIfNeeded(String targetUrl) {
        if (targetUrl.endsWith("/")) {
            targetUrl = targetUrl.substring(0, targetUrl.length() - 1);
        }
        if (!targetUrl.endsWith(SERVICES_COLLECTOR_EVENT)) {
            if (targetUrl.endsWith(SERVICES_COLLECTOR_RAW)) {
                targetUrl = targetUrl.substring(0, targetUrl.length() - RAW.length()) + EVENT;
            } else if (targetUrl.endsWith(SERVICES_COLLECTOR)) {
                targetUrl += EVENT;
            } else {
                targetUrl += SERVICES_COLLECTOR_EVENT;
            }
        }
        return targetUrl;
    }

    /**
     * Builds the Splunk HEC payload from a notification, splitting multiple events into
     * concatenated single-event payloads for batch ingestion.
     */
    static String buildSplunkPayload(SplunkNotification notification) {
        JsonObject base = new JsonObject();
        base.put("org_id", notification.getOrgId());
        base.put("account_id", notification.accountId);

        JsonArray events = notification.events;

        if (events == null || events.isEmpty() || events.size() == 1) {
            base.put("events", events);
            return wrapPayload(base).encode();
        }

        // Reuse the same base object across iterations: wrapPayload() creates a new wrapper
        // referencing base, and .encode() serializes it to a String before the next put() mutates base.
        List<String> wrappedPayloads = new ArrayList<>(events.size());
        for (int i = 0; i < events.size(); i++) {
            base.put("events", new JsonArray(List.of(events.getJsonObject(i))));
            wrappedPayloads.add(wrapPayload(base).encode());
        }
        return String.join("", wrappedPayloads);
    }

    private static JsonObject wrapPayload(JsonObject payload) {
        JsonObject result = new JsonObject();
        result.put(SOURCE_KEY, SOURCE_VALUE);
        result.put(SOURCE_TYPE_KEY, SOURCE_TYPE_VALUE);
        result.put(EVENT_KEY, payload);
        return result;
    }
}
