package com.redhat.cloud.notifications.processors.webhooks;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.events.IntegrationDisabledNotifier;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
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
import io.vertx.ext.web.client.impl.HttpRequestImpl;
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
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.models.NotificationHistory.getHistoryStub;

@ApplicationScoped
public class WebhookTypeProcessor implements EndpointTypeProcessor {

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
    public List<NotificationHistory> process(Event event, List<Endpoint> endpoints) {
        return endpoints.stream()
                .map(endpoint -> {
                    Notification notification = new Notification(event, endpoint);
                    return process(notification);
                })
                .collect(Collectors.toList());
    }

    private NotificationHistory process(Notification item) {
        processedCount.increment();
        Endpoint endpoint = item.getEndpoint();
        WebhookProperties properties = endpoint.getProperties(WebhookProperties.class);

        final HttpRequest<Buffer> req = getWebClient(properties.getDisableSslVerification())
                .requestAbs(HttpMethod.valueOf(properties.getMethod().name()), properties.getUrl());

        if (properties.getSecretToken() != null && !properties.getSecretToken().isBlank()) {
            req.putHeader(TOKEN_HEADER, properties.getSecretToken());
        }

        if (properties.getBasicAuthentication() != null) {
            req.basicAuthentication(properties.getBasicAuthentication().getUsername(), properties.getBasicAuthentication().getPassword());
        }

        JsonObject payload = transformer.transform(item.getEvent().getAction());

        return doHttpRequest(item, req, payload, properties.getMethod().name(), properties.getUrl());
    }

    private WebClient getWebClient(boolean disableSSLVerification) {
        if (disableSSLVerification) {
            return unsecuredWebClient;
        } else {
            return securedWebClient;
        }
    }

    public NotificationHistory doHttpRequest(Notification item, HttpRequest<Buffer> req, JsonObject payload, String method, String url) {
        final long startTime = System.currentTimeMillis();

        try {
            return Failsafe.with(retryPolicy).get(() -> {

                // TODO NOTIF-488 We may want to move to a non-reactive HTTP client in the future.
                HttpResponse<Buffer> resp = req.sendJsonObject(payload).await().atMost(awaitTimeout);
                NotificationHistory history = buildNotificationHistory(item, startTime);

                HttpRequestImpl<Buffer> reqImpl = (HttpRequestImpl<Buffer>) req.getDelegate();

                boolean serverError = false;
                boolean isEmailEndpoint = item.getEndpoint().getType() == EMAIL_SUBSCRIPTION;
                boolean shouldResetEndpointServerErrors = false;
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    // Accepted
                    Log.debugf("Webhook request to %s was successful: %d", url, resp.statusCode());
                    history.setInvocationResult(true);
                    shouldResetEndpointServerErrors = true;
                } else if (resp.statusCode() >= 500) {
                    // Temporary error, allow retry
                    serverError = true;
                    Log.debugf("Webhook request to %s failed: %d %s", url, resp.statusCode(), resp.statusMessage());
                    history.setInvocationResult(false);
                    if (featureFlipper.isDisableWebhookEndpointsOnFailure()) {
                        if (!isEmailEndpoint) {
                            /*
                             * The target endpoint returned a 5xx status. That kind of error happens in case of remote
                             * server failure, which is usually something temporary. Sending another notification to
                             * the same endpoint may work in the future, so the endpoint is only disabled if the max
                             * number of endpoint failures allowed from the configuration is exceeded.
                             */
                            boolean disabled = endpointRepository.incrementEndpointServerErrors(item.getEndpoint().getId(), maxServerErrors);
                            if (disabled) {
                                disabledWebhooksServerErrorCount.increment();
                                Log.infof("Endpoint %s was disabled because we received too many 5xx status while calling it", item.getEndpoint().getId());
                                integrationDisabledNotifier.tooManyServerErrors(item.getEndpoint(), maxServerErrors);
                            }
                        }
                    }
                } else {
                    // Redirects etc should have been followed by the vertx (test this)
                    Log.debugf("Webhook request to %s failed: %d %s %s", url, resp.statusCode(), resp.statusMessage(), payload);
                    history.setInvocationResult(false);
                    // TODO NOTIF-512 Should we disable endpoints in case of 3xx status code?
                    if (featureFlipper.isDisableWebhookEndpointsOnFailure()) {
                        if (!isEmailEndpoint && resp.statusCode() >= 400 && resp.statusCode() < 500) {
                            /*
                             * The target endpoint returned a 4xx status. That kind of error requires an update of the
                             * endpoint settings (URL, secret token...). The endpoint will most likely never return a
                             * successful status code with the current settings, so it is disabled immediately.
                             */
                            boolean disabled = endpointRepository.disableEndpoint(item.getEndpoint().getId());
                            if (disabled) {
                                disabledWebhooksClientErrorCount.increment();
                                Log.infof("Endpoint %s was disabled because we received a 4xx status while calling it", item.getEndpoint().getId());
                                integrationDisabledNotifier.clientError(item.getEndpoint(), resp.statusCode());
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
                        boolean reset = endpointRepository.resetEndpointServerErrors(item.getEndpoint().getId());
                        if (reset) {
                            Log.tracef("The server errors counter of endpoint %s was just reset", item.getEndpoint().getId());
                        }
                    }
                }

                if (!history.isInvocationResult()) {
                    JsonObject details = new JsonObject();
                    details.put("url", url); // TODO does this show http or https
                    details.put("method", method);
                    details.put("code", resp.statusCode());
                    // This isn't async body reading, lets hope vertx handles it async underneath before calling this apply method
                    details.put("response_body", resp.bodyAsString());
                    history.setDetails(details.getMap());
                }

                if (serverError) {
                    throw new ServerErrorException(history);
                }
                return history;
            });
        } catch (Exception e) {
            if (e instanceof ServerErrorException) {
                return ((ServerErrorException) e).getNotificationHistory();
            }

            NotificationHistory history = buildNotificationHistory(item, startTime);

            HttpRequestImpl<Buffer> reqImpl = (HttpRequestImpl<Buffer>) req.getDelegate();

            Log.debugf("Failed: %s", e.getMessage());

            // TODO Duplicate code with the error return code part
            JsonObject details = new JsonObject();
            details.put("url", reqImpl.uri());
            details.put("method", method);
            details.put("error_message", e.getMessage()); // TODO This message isn't always the most descriptive..
            history.setDetails(details.getMap());
            return history;
        }
    }


    private NotificationHistory buildNotificationHistory(Notification item, long startTime) {
        long invocationTime = System.currentTimeMillis() - startTime;
        return getHistoryStub(item.getEndpoint(), item.getEvent(), invocationTime, UUID.randomUUID());
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
