package com.redhat.cloud.notifications.processors.webhooks;

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
import io.vertx.core.VertxException;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.impl.HttpRequestImpl;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.NotificationHistory.getHistoryStub;

@ApplicationScoped
public class WebhookTypeProcessor implements EndpointTypeProcessor {

    private static final Logger LOGGER = Logger.getLogger(WebhookTypeProcessor.class);
    private static final String TOKEN_HEADER = "X-Insight-Token";
    private static final String CONNECTION_CLOSED_MSG = "Connection was closed";

    @ConfigProperty(name = "processor.webhook.retry.max-attempts", defaultValue = "3")
    int maxRetryAttempts;

    @ConfigProperty(name = "processor.webhook.retry.back-off.initial-value", defaultValue = "1S")
    Duration initialRetryBackOff;

    @ConfigProperty(name = "processor.webhook.retry.back-off.max-value", defaultValue = "30S")
    Duration maxRetryBackOff;

    @Inject
    @SslVerificationEnabled
    WebClient securedWebClient;

    @Inject
    @SslVerificationDisabled
    WebClient unsecuredWebClient;

    @Inject
    BaseTransformer transformer;

    @Inject
    MeterRegistry registry;

    private Counter processedCount;
    private RetryPolicy<Object> retryPolicy;

    @PostConstruct
    void postConstruct() {
        processedCount = registry.counter("processor.webhook.processed");
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

        return doHttpRequest(item, req, payload);
    }

    private WebClient getWebClient(boolean disableSSLVerification) {
        if (disableSSLVerification) {
            return unsecuredWebClient;
        } else {
            return securedWebClient;
        }
    }

    public NotificationHistory doHttpRequest(Notification item, HttpRequest<Buffer> req, JsonObject payload) {
        final long startTime = System.currentTimeMillis();

        try {
            return Failsafe.with(retryPolicy).get(() -> {

                // TODO NOTIF-488 We may want to move to a non-reactive HTTP client in the future.
                HttpResponse<Buffer> resp = req.sendJsonObject(payload).await().indefinitely();
                NotificationHistory history = buildNotificationHistory(item, startTime);

                HttpRequestImpl<Buffer> reqImpl = (HttpRequestImpl<Buffer>) req.getDelegate();

                boolean serverError = false;
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    // Accepted
                    LOGGER.debugf("Webhook request to %s was successful: %d", reqImpl.host(), resp.statusCode());
                    history.setInvocationResult(true);
                } else if (resp.statusCode() >= 500) {
                    // Temporary error, allow retry
                    serverError = true;
                    LOGGER.debugf("Webhook request to %s failed: %d %s", reqImpl.host(), resp.statusCode(), resp.statusMessage());
                    history.setInvocationResult(false);
                } else {
                    // Disable the target endpoint, it's not working correctly for us (such as 400)
                    // must be manually re-enabled
                    // Redirects etc should have been followed by the vertx (test this)
                    LOGGER.debugf("Webhook request to %s failed: %d %s %s", reqImpl.host(), resp.statusCode(), resp.statusMessage(), payload);
                    history.setInvocationResult(false);
                }

                if (!history.isInvocationResult()) {
                    JsonObject details = new JsonObject();
                    details.put("url", getCallUrl(reqImpl));
                    details.put("method", reqImpl.method().name());
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

            LOGGER.debugf("Failed: %s", e.getMessage());

            // TODO Duplicate code with the error return code part
            JsonObject details = new JsonObject();
            details.put("url", reqImpl.uri());
            details.put("method", reqImpl.method());
            details.put("error_message", e.getMessage()); // TODO This message isn't always the most descriptive..
            history.setDetails(details.getMap());
            return history;
        }
    }

    private String getCallUrl(HttpRequestImpl<Buffer> reqImpl) {
        String protocol;
        if (reqImpl.ssl()) {
            protocol = "https";
        } else {
            protocol = "http";
        }

        return protocol + "://" + reqImpl.host() + ":" + reqImpl.port() + reqImpl.uri();
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
