package com.redhat.cloud.notifications.processors.webhooks;

import com.redhat.cloud.notifications.DelayedThrower;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.events.IntegrationDisabledNotifier;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.webclient.SslVerificationDisabled;
import com.redhat.cloud.notifications.processors.webclient.SslVerificationEnabled;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.ConnectTimeoutException;
import io.quarkus.logging.Log;
import io.vertx.core.VertxException;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.events.EndpointProcessor.DELAYED_EXCEPTION_MSG;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.models.NotificationHistory.getHistoryStub;

@ApplicationScoped
public class WebhookTypeProcessor extends EndpointTypeProcessor {

    public static final String DISABLED_WEBHOOKS_COUNTER = "processor.webhook.disabled.endpoints";
    public static final String ERROR_TYPE_TAG_KEY = "error_type";
    public static final String CLIENT_TAG_VALUE = "client";
    public static final String SERVER_TAG_VALUE = "server";

    private static final String TOKEN_HEADER = "X-Insight-Token";
    private static final String CONNECTION_CLOSED_MSG = "Connection was closed";

    @ConfigProperty(name = "processor.webhook.retry.max-attempts", defaultValue = "3")
    int maxRetryAttempts;

    @ConfigProperty(name = "processor.webhook.retry.back-off.initial-value", defaultValue = "1S")
    Duration initialRetryBackOff;

    @ConfigProperty(name = "processor.webhook.retry.back-off.max-value", defaultValue = "30S")
    Duration maxRetryBackOff;

    @ConfigProperty(name = "processor.webhook.await-timeout", defaultValue = "60S")
    Duration awaitTimeout;

    @ConfigProperty(name = "processor.webhook.max-server-errors", defaultValue = "10")
    int maxServerErrors;

    @Inject
    @SslVerificationEnabled
    WebClient securedWebClient;

    @Inject
    @SslVerificationDisabled
    WebClient unsecuredWebClient;

    @Inject
    BaseTransformer transformer;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    IntegrationDisabledNotifier integrationDisabledNotifier;

    @Inject
    MeterRegistry registry;

    private Counter processedCount;
    private Counter disabledWebhooksClientErrorCount;
    private Counter disabledWebhooksServerErrorCount;
    private RetryPolicy<Object> retryPolicy;

    @PostConstruct
    void postConstruct() {
        processedCount = registry.counter("processor.webhook.processed");
        disabledWebhooksClientErrorCount = registry.counter(DISABLED_WEBHOOKS_COUNTER, ERROR_TYPE_TAG_KEY, CLIENT_TAG_VALUE);
        disabledWebhooksServerErrorCount = registry.counter(DISABLED_WEBHOOKS_COUNTER, ERROR_TYPE_TAG_KEY, SERVER_TAG_VALUE);
        retryPolicy = RetryPolicy.builder()
                .handleIf(this::shouldRetry)
                .withBackoff(initialRetryBackOff, maxRetryBackOff)
                .withMaxRetries(maxRetryAttempts)
                .build();
    }

    @Override
    public void process(Event event, List<Endpoint> endpoints) {
        DelayedThrower.throwEventually(DELAYED_EXCEPTION_MSG, accumulator -> {
            for (Endpoint endpoint : endpoints) {
                try {
                    process(event, endpoint);
                } catch (Exception e) {
                    accumulator.add(e);
                }
            }
        });
    }

    private void process(Event event, Endpoint endpoint) {
        processedCount.increment();
        WebhookProperties properties = endpoint.getProperties(WebhookProperties.class);

        final HttpRequest<Buffer> req = getWebClient(properties.getDisableSslVerification())
                .requestAbs(HttpMethod.valueOf(properties.getMethod().name()), properties.getUrl());

        if (properties.getSecretToken() != null && !properties.getSecretToken().isBlank()) {
            req.putHeader(TOKEN_HEADER, properties.getSecretToken());
        }

        if (properties.getBasicAuthentication() != null) {
            req.basicAuthentication(properties.getBasicAuthentication().getUsername(), properties.getBasicAuthentication().getPassword());
        }

        JsonObject payload = transformer.toJsonObject(event.getAction());

        doHttpRequest(event, endpoint, req, payload, properties.getMethod().name(), properties.getUrl());
    }

    private WebClient getWebClient(boolean disableSSLVerification) {
        if (disableSSLVerification) {
            return unsecuredWebClient;
        } else {
            return securedWebClient;
        }
    }

    public void doHttpRequest(Event event, Endpoint endpoint, HttpRequest<Buffer> req, JsonObject payload, String method, String url) {
        final long startTime = System.currentTimeMillis();

        try {
            Failsafe.with(retryPolicy).run(() -> {

                // TODO NOTIF-488 We may want to move to a non-reactive HTTP client in the future.
                HttpResponse<Buffer> resp = req.sendJsonObject(payload).await().atMost(awaitTimeout);
                NotificationHistory history = buildNotificationHistory(event, endpoint, startTime);

                boolean serverError = false;
                boolean isEmailEndpoint = endpoint.getType() == EMAIL_SUBSCRIPTION;
                boolean shouldResetEndpointServerErrors = false;
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    // Accepted
                    Log.debugf("Webhook request to %s was successful: %d", url, resp.statusCode());
                    history.setStatus(NotificationStatus.SUCCESS);
                    shouldResetEndpointServerErrors = true;
                } else if (resp.statusCode() >= 500) {
                    // Temporary error, allow retry
                    serverError = true;
                    Log.debugf("Webhook request to %s failed: %d %s", url, resp.statusCode(), resp.statusMessage());
                    history.setStatus(NotificationStatus.FAILED_INTERNAL);
                    if (featureFlipper.isDisableWebhookEndpointsOnFailure()) {
                        if (!isEmailEndpoint) {
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
                } else {
                    // Redirects etc should have been followed by the vertx (test this)
                    if (isEmailEndpoint) {
                        Log.warnf("Webhook request to %s failed: %d %s %s", url, resp.statusCode(), resp.statusMessage(), payload);
                    } else {
                        Log.debugf("Webhook request to %s failed: %d %s %s", url, resp.statusCode(), resp.statusMessage(), payload);
                    }
                    history.setStatus(NotificationStatus.FAILED_INTERNAL);
                    // TODO NOTIF-512 Should we disable endpoints in case of 3xx status code?
                    if (featureFlipper.isDisableWebhookEndpointsOnFailure()) {
                        if (!isEmailEndpoint && resp.statusCode() >= 400 && resp.statusCode() < 500) {
                            /*
                             * The target endpoint returned a 4xx status. That kind of error requires an update of the
                             * endpoint settings (URL, secret token...). The endpoint will most likely never return a
                             * successful status code with the current settings, so it is disabled immediately.
                             */
                            boolean disabled = endpointRepository.disableEndpoint(endpoint.getId());
                            if (disabled) {
                                disabledWebhooksClientErrorCount.increment();
                                Log.infof("Endpoint %s was disabled because we received a 4xx status while calling it", endpoint.getId());
                                integrationDisabledNotifier.clientError(endpoint, resp.statusCode());
                            }
                        } else {
                            /*
                             * 3xx status codes may be considered has a failure soon, but first we need to confirm
                             * that Vert.x is correctly following the redirections.
                             */
                            shouldResetEndpointServerErrors = true;
                        }
                    }
                }

                if (featureFlipper.isDisableWebhookEndpointsOnFailure()) {
                    if (!isEmailEndpoint && shouldResetEndpointServerErrors) {
                        // When a target endpoint is successfully called, its server errors counter is reset in the DB.
                        boolean reset = endpointRepository.resetEndpointServerErrors(endpoint.getId());
                        if (reset) {
                            Log.tracef("The server errors counter of endpoint %s was just reset", endpoint.getId());
                        }
                    }
                }

                if (history.getStatus() == NotificationStatus.FAILED_INTERNAL) {
                    JsonObject details = new JsonObject();
                    details.put("url", url);
                    details.put("method", method);
                    details.put("code", resp.statusCode());
                    details.put("response_body", resp.bodyAsString());
                    history.setDetails(details.getMap());
                }

                if (serverError) {
                    throw new ServerErrorException(history);
                }
                persistNotificationHistory(history);
            });
        } catch (Exception e) {
            NotificationHistory history;
            if (e instanceof ServerErrorException) {
                history = ((ServerErrorException) e).getNotificationHistory();
            } else {
                history = buildNotificationHistory(event, endpoint, startTime);

                Log.debugf("Failed: %s", e.getMessage());

                JsonObject details = new JsonObject();
                details.put("url", url);
                details.put("method", method);
                details.put("error_message", e.getMessage()); // TODO This message isn't always the most descriptive..
                history.setDetails(details.getMap());
            }
            persistNotificationHistory(history);
        }
    }

    private NotificationHistory buildNotificationHistory(Event event, Endpoint endpoint, long startTime) {
        long invocationTime = System.currentTimeMillis() - startTime;
        return getHistoryStub(endpoint, event, invocationTime, UUID.randomUUID());
    }

    /**
     * Returns {@code true} if we should retry when the given {@code throwable} is thrown during a webhook call.
     * <ul>
     *     <li>{@link ServerErrorException} is thrown when the call was successful but the remote server replied with a 5xx HTTP status.</li>
     *     <li>{@link IOException} is thrown when the connection between us and the remote server was reset during the call.</li>
     *     <li>{@link ConnectTimeoutException} is thrown when the remote server did not respond at all to our call.</li>
     * </ul>
     */
    private boolean shouldRetry(Throwable throwable) {
        return throwable instanceof ServerErrorException ||
                throwable instanceof IOException ||
                throwable instanceof ConnectTimeoutException ||
                throwable instanceof VertxException && CONNECTION_CLOSED_MSG.equals(throwable.getMessage());
    }
}
