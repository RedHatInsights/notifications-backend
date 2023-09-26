package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class EndpointErrorFromConnectorHelper {

    public static final String INCREMENT_ENDPOINT_SERVER_ERRORS = "incrementEndpointServerErrors";
    public static final String DISABLE_ENDPOINT_CLIENT_ERRORS = "disableEndpointClientErrors";
    public static final String HTTP_STATUS_CODE = "HttpStatusCode";

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    IntegrationDisabledNotifier integrationDisabledNotifier;

    @Inject
    MeterRegistry registry;

    @ConfigProperty(name = "processor.webhook.max-server-errors", defaultValue = "10")
    int maxServerErrors;

    @Inject
    NotificationHistoryRepository notificationHistoryRepository;

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
        JsonObject data = new JsonObject(payload.getString("data"));
        if (strHistoryId != null) {
            boolean shouldIncrementServerError = data.getBoolean(INCREMENT_ENDPOINT_SERVER_ERRORS, false);
            boolean shouldDisableEndpointClientError = data.getBoolean(DISABLE_ENDPOINT_CLIENT_ERRORS, false);

            if (data.getBoolean("successful", false)) {
                boolean reset = endpointRepository.resetEndpointServerErrors(endpoint.getId());
                if (reset) {
                    Log.infof("The server errors counter of endpoint %s was just reset", endpoint.getId());
                }
            } else if (shouldIncrementServerError || shouldDisableEndpointClientError) {
                if (shouldDisableEndpointClientError) {
                    /*
                     * The target endpoint returned a 4xx status. That kind of error requires an update of the
                     * endpoint settings (URL, secret token...). The endpoint will most likely never return a
                     * successful status code with the current settings, so it is disabled immediately.
                     */

                    boolean disabled = endpointRepository.disableEndpoint(endpoint.getId());
                    if (disabled) {
                        disabledWebhooksClientErrorCount.increment();
                        Log.infof("Endpoint %s was disabled because we received a 4xx status while calling it", endpoint.getId());
                        integrationDisabledNotifier.clientError(endpoint, data.getJsonObject("details").getInteger(HTTP_STATUS_CODE, 400));
                    }
                }

                if (shouldIncrementServerError) {
                    /*
                     * The target endpoint returned a 5xx status. That kind of error happens in case of remote
                     * server failure, which is usually something temporary. Sending another notification to
                     * the same endpoint may work in the future, so the endpoint is only disabled if the max
                     * number of endpoint failures allowed from the configuration is exceeded.
                     */
                    boolean disabled = endpointRepository.incrementEndpointServerErrors(endpoint.getId(), maxServerErrors);
                    if (disabled) {
                        disabledWebhooksServerErrorCount.increment();
                        Log.infof("Endpoint %s was disabled because we received too many 5xx status while calling it", endpoint.getId());
                        integrationDisabledNotifier.tooManyServerErrors(endpoint, maxServerErrors);
                    }
                }
            }
        }
    }
}
