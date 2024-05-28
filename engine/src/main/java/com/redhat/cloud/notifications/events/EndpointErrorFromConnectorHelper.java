package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.Set;

import static com.redhat.cloud.notifications.events.HttpErrorType.CONNECTION_REFUSED;
import static com.redhat.cloud.notifications.events.HttpErrorType.CONNECT_TIMEOUT;
import static com.redhat.cloud.notifications.events.HttpErrorType.HTTP_4XX;
import static com.redhat.cloud.notifications.events.HttpErrorType.HTTP_5XX;
import static com.redhat.cloud.notifications.events.HttpErrorType.SOCKET_TIMEOUT;
import static com.redhat.cloud.notifications.events.HttpErrorType.SSL_HANDSHAKE;
import static com.redhat.cloud.notifications.events.HttpErrorType.UNKNOWN_HOST;

@ApplicationScoped
public class EndpointErrorFromConnectorHelper {

    private static final Set<HttpErrorType> HTTP_SERVER_ERRORS = Set.of(SOCKET_TIMEOUT, CONNECT_TIMEOUT, CONNECTION_REFUSED, HTTP_5XX, SSL_HANDSHAKE, UNKNOWN_HOST);

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    IntegrationDisabledNotifier integrationDisabledNotifier;

    @Inject
    MeterRegistry registry;

    private Counter disabledWebhooksServerErrorCount;
    private Counter disabledWebhooksClientErrorCount;
    public static final String CLIENT_TAG_VALUE = "client";
    public static final String SERVER_TAG_VALUE = "server";
    public static final String ERROR_TYPE_TAG_KEY = "error_type";
    public static final String DISABLED_WEBHOOKS_COUNTER = "processor.webhook.disabled.endpoints";

    @PostConstruct
    void postConstruct() {
        disabledWebhooksServerErrorCount = registry.counter(DISABLED_WEBHOOKS_COUNTER, ERROR_TYPE_TAG_KEY, SERVER_TAG_VALUE);
        disabledWebhooksClientErrorCount = registry.counter(DISABLED_WEBHOOKS_COUNTER, ERROR_TYPE_TAG_KEY, CLIENT_TAG_VALUE);
    }

    public void manageEndpointDisablingIfNeeded(Endpoint endpoint, JsonObject payload) {
        String strHistoryId = payload.getString("id");
        if (endpoint == null) {
            Log.debugf("Unable to update endpoint data from history %s, because it no longer exists", strHistoryId);
            return;
        }

        JsonObject data = new JsonObject(payload.getString("data"));
        if (strHistoryId != null) {

            if (data.getBoolean("successful", false)) {
                boolean reset = endpointRepository.resetEndpointServerErrors(endpoint.getId());
                if (reset) {
                    Log.infof("The server errors counter of endpoint %s was just reset", endpoint.getId());
                }
            } else if (data.containsKey("error")) {
                JsonObject error = data.getJsonObject("error");
                Optional<HttpErrorType> httpErrorType = getHttpErrorType(error);
                if (httpErrorType.isPresent()) {
                    Integer statusCode = error.getInteger("http_status_code");

                    if (httpErrorType.get() == HTTP_4XX) {
                        /*
                         * The target endpoint returned a 4xx status. That kind of error requires an update of the
                         * endpoint settings (URL, secret token...). The endpoint will most likely never return a
                         * successful status code with the current settings, so it is disabled immediately.
                         */
                        boolean disabled = endpointRepository.disableEndpoint(endpoint.getId());
                        if (disabled) {
                            disabledWebhooksClientErrorCount.increment();
                            Log.infof("Endpoint %s was disabled because we received a 4xx status while calling it", endpoint.getId());
                            integrationDisabledNotifier.notify(endpoint, httpErrorType.get(), statusCode, 1);
                        }
                    } else if (HTTP_SERVER_ERRORS.contains(httpErrorType.get())) {
                        /*
                         * The target endpoint returned a server error. That kind of error happens in case of remote
                         * server failure, which is usually something temporary. Sending another notification to
                         * the same endpoint may work in the future, so the endpoint is only disabled if the max
                         * number of endpoint failures allowed from the configuration is exceeded.
                         */
                        int deliveryAttempts = error.getInteger("delivery_attempts", 1);
                        boolean disabled = endpointRepository.incrementEndpointServerErrors(endpoint.getId(), deliveryAttempts);
                        if (disabled) {
                            disabledWebhooksServerErrorCount.increment();
                            Log.infof("Endpoint %s was disabled because it caused too many 5xx errors or IOExceptions while calling it", endpoint.getId());
                            integrationDisabledNotifier.notify(endpoint, httpErrorType.get(), statusCode, endpoint.getServerErrors());
                        }
                    }
                }
            }
        }
    }

    private static Optional<HttpErrorType> getHttpErrorType(JsonObject error) {
        String errorType = error.getString("error_type");
        if (errorType == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(HttpErrorType.valueOf(errorType));
        } catch (IllegalArgumentException e) {
            Log.warnf(e, "Unknown %s: %s", HttpErrorType.class.getName(), errorType);
            return Optional.empty();
        }
    }
}
