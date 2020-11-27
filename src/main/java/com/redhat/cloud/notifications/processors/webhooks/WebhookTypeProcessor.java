package com.redhat.cloud.notifications.processors.webhooks;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.webhooks.transformers.BaseTransformer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.impl.HttpRequestImpl;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.WebClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.net.ConnectException;
import java.net.UnknownHostException;
import org.jboss.logging.Logger;

@ApplicationScoped
public class WebhookTypeProcessor implements EndpointTypeProcessor {

    private static final Logger LOG = Logger.getLogger(WebhookTypeProcessor.class);

    private static final String TOKEN_HEADER = "X-Insight-Token";

    @Inject
    Vertx vertx;

    @Inject
    BaseTransformer transformer;

    MeterRegistry registry;

    private Counter processedCount;

    public WebhookTypeProcessor(MeterRegistry registry) {
        this.registry = registry;
        processedCount = registry.counter("processor.webhook.processed");
    }

    public Uni<NotificationHistory> process(Notification item) {
        processedCount.increment();
        Endpoint endpoint = item.getEndpoint();
        WebhookAttributes properties = (WebhookAttributes) endpoint.getProperties();

        WebClientOptions options = new WebClientOptions()
                .setSsl(!properties.isDisableSSLVerification())
                .setConnectTimeout(3000); // TODO Should this be configurable by the system? We need a maximum in any case

        final HttpRequest<Buffer> req = WebClient.create(vertx, options)
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

    public Uni<NotificationHistory> doHttpRequest(Notification item, HttpRequest<Buffer> req, Uni<JsonObject> payload) {
        final long startTime = System.currentTimeMillis();

        return payload.onItem()
                .transformToUni(json -> req.sendJsonObject(json)
                        .onItem().transform(resp -> {
                            System.out.println("Calling webhook");
                            final long endTime = System.currentTimeMillis();
                            // Default result is false
                            NotificationHistory history = getHistoryStub(item, endTime - startTime);

                            if (resp.statusCode() >= 200 && resp.statusCode() <= 300) {
                                // Accepted
                                history.setInvocationResult(true);
                            } else if (resp.statusCode() > 500) {
                                // Temporary error, allow retry
                                System.out.println("Couldn't send EMail " + resp.statusCode() + " " + resp.body());
                                LOG.warn("Failed to send: " + json + " Response: " + resp.statusCode() + " " + resp.body());
                                history.setInvocationResult(false);
                            } else {
                                // Disable the target endpoint, it's not working correctly for us (such as 400)
                                // must eb manually re-enabled
                                // Redirects etc should have been followed by the vertx (test this)
                                System.out.println("Couldn't send EMail " + resp.statusCode() + " " + resp.body());
                                LOG.warn("Failed to send: " + json + " Response: " + resp.statusCode() + " " + resp.body());
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
                                history.setDetails(details);
                            }

                            return history;
                        }).onFailure().recoverWithItem(t -> {
                            // TODO Duplicate code with the success part
                            final long endTime = System.currentTimeMillis();
                            NotificationHistory history = getHistoryStub(item, endTime - startTime);

                            HttpRequestImpl<Buffer> reqImpl = (HttpRequestImpl<Buffer>) req.getDelegate();

                            // TODO Duplicate code with the error return code part
                            JsonObject details = new JsonObject();
                            details.put("url", reqImpl.uri());
                            details.put("method", reqImpl.method());
                            details.put("error_message", t.getMessage()); // TODO This message isn't always the most descriptive..
                            history.setDetails(details);

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

    private NotificationHistory getHistoryStub(Notification item, long invocationTime) {
        NotificationHistory history = new NotificationHistory();
        history.setInvocationTime(invocationTime);
        history.setEndpoint(item.getEndpoint());
        history.setTenant(item.getEndpoint().getTenant());
        history.setEventId(item.getEventId());
        history.setInvocationResult(false);
        return history;
    }
}
