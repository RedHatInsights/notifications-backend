package com.redhat.cloud.notifications.processors.webhooks;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.webclient.SslVerificationDisabled;
import com.redhat.cloud.notifications.processors.webclient.SslVerificationEnabled;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.impl.HttpRequestImpl;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.WebClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static com.redhat.cloud.notifications.models.NotificationHistory.getHistoryStub;

@ApplicationScoped
public class WebhookTypeProcessor implements EndpointTypeProcessor {

    private final Logger log = Logger.getLogger(this.getClass().getName());

    private static final String TOKEN_HEADER = "X-Insight-Token";

    @Inject
    @SslVerificationEnabled
    WebClient securedWebClient;

    @Inject
    @SslVerificationDisabled
    WebClient unsecuredWebClient;

    @Inject
    BaseTransformer transformer;

    MeterRegistry registry;

    private Counter processedCount;

    public WebhookTypeProcessor(MeterRegistry registry) {
        this.registry = registry;
        processedCount = registry.counter("processor.webhook.processed");
    }

    @Override
    public Multi<NotificationHistory> process(Action action, List<Endpoint> endpoints) {
        return Multi.createFrom().iterable(endpoints)
                .onItem().transformToUniAndConcatenate(endpoint -> {
                    Notification notification = new Notification(action, endpoint);
                    return process(notification);
                });
    }

    private Uni<NotificationHistory> process(Notification item) {
        processedCount.increment();
        Endpoint endpoint = item.getEndpoint();
        WebhookProperties properties = endpoint.getProperties(WebhookProperties.class);

        final HttpRequest<Buffer> req = getWebClient(properties.getDisableSslVerification())
                .rawAbs(properties.getMethod().name(), properties.getUrl());

        if (properties.getSecretToken() != null && !properties.getSecretToken().isBlank()) {
            req.putHeader(TOKEN_HEADER, properties.getSecretToken());
        }

        if (properties.getBasicAuthentication() != null) {
            req.basicAuthentication(properties.getBasicAuthentication().getUsername(), properties.getBasicAuthentication().getPassword());
        }

        Uni<JsonObject> payload = transformer.transform(item.getAction());

        return doHttpRequest(item, req, payload);
    }

    private WebClient getWebClient(boolean disableSSLVerification) {
        if (disableSSLVerification) {
            return unsecuredWebClient;
        } else {
            return securedWebClient;
        }
    }

    public Uni<NotificationHistory> doHttpRequest(Notification item, HttpRequest<Buffer> req, Uni<JsonObject> payload) {
        final long startTime = System.currentTimeMillis();

        return payload.onItem()
                .transformToUni(json -> req.sendJsonObject(json)
                        .onItem().transform(resp -> {
                            final long endTime = System.currentTimeMillis();
                            // Default result is false
                            NotificationHistory history = getHistoryStub(item, endTime - startTime, UUID.randomUUID());

                            if (resp.statusCode() >= 200 && resp.statusCode() <= 300) {
                                // Accepted
                                log.fine("Target endpoint successful: " + resp.statusCode());
                                history.setInvocationResult(true);
                            } else if (resp.statusCode() >= 500) {
                                // Temporary error, allow retry
                                log.fine("Target endpoint server error: " + resp.statusCode() + " " + resp.statusMessage());
                                history.setInvocationResult(false);
                            } else {
                                // Disable the target endpoint, it's not working correctly for us (such as 400)
                                // must be manually re-enabled
                                // Redirects etc should have been followed by the vertx (test this)
                                log.fine("Target endpoint error: " + resp.statusCode() + " " + resp.statusMessage() + " " + json);
                                history.setInvocationResult(false);
                            }

                            HttpRequestImpl<Buffer> reqImpl = (HttpRequestImpl<Buffer>) req.getDelegate();

                            if (!history.isInvocationResult()) {
                                JsonObject details = new JsonObject();
                                details.put("url", getCallUrl(reqImpl));
                                details.put("method", reqImpl.rawMethod());
                                details.put("code", resp.statusCode());
                                // This isn't async body reading, lets hope vertx handles it async underneath before calling this apply method
                                details.put("response_body", resp.bodyAsString());
                                history.setDetails(details.getMap());
                            }

                            return history;
                        }).onFailure().recoverWithItem(t -> {

                            // TODO Duplicate code with the success part
                            final long endTime = System.currentTimeMillis();
                            NotificationHistory history = getHistoryStub(item, endTime - startTime, UUID.randomUUID());

                            HttpRequestImpl<Buffer> reqImpl = (HttpRequestImpl<Buffer>) req.getDelegate();

                            log.fine("Failed: " + t.getMessage());

                            // TODO Duplicate code with the error return code part
                            JsonObject details = new JsonObject();
                            details.put("url", reqImpl.uri());
                            details.put("method", reqImpl.method());
                            details.put("error_message", t.getMessage()); // TODO This message isn't always the most descriptive..
                            history.setDetails(details.getMap());

                            if (t instanceof ConnectException) {
                                // Connection refused for example
                                ConnectException ce = (ConnectException) t;
                            } else if (t instanceof UnknownHostException) {
                                UnknownHostException uhe = (UnknownHostException) t;
                            }

                            // io.netty.channel.ConnectTimeoutException: connection timed out: webhook.site/46.4.105.116:443
                            return history;
                        })
                );
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

}
