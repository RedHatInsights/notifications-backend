package com.redhat.cloud.notifications.webhooks;

import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.db.NotificationResources;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.WebClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.net.ConnectException;
import java.net.UnknownHostException;

@ApplicationScoped
public class WebhookProcessor {

    private final static String TOKEN_HEADER = "X-Insight-Token";

    @Inject
    Vertx vertx;

    @Inject
    EndpointResources resources;

    @Inject
    NotificationResources notifResources;

    // TODO This process(Uni<Void>) should probably be an interface that can be fetched with injections (like action plugins in policies-engine)
    public Uni<Void> process(Notification item) {
        return resources.getActiveEndpointsPerType(item.getTenant(), Endpoint.EndpointType.WEBHOOK)
                .onItem().produceUni(endpoint -> {
                    System.out.printf("Processing webhook: %s\n", endpoint.toString());
                    WebhookAttributes properties = (WebhookAttributes) endpoint.getProperties();

                    WebClientOptions options = new WebClientOptions()
                            .setSsl(!properties.isDisableSSLVerification())
                            .setConnectTimeout(3000); // TODO Should this be configurable by the user? We need a maximum in any case

                    // TODO Add here the method type as the call
                    HttpRequest<Buffer> postReq = WebClient.create(vertx, options)
                            .postAbs(properties.getUrl());

                    if (properties.getSecretToken() != null && !properties.getSecretToken().isBlank()) {
                        postReq = postReq.putHeader(TOKEN_HEADER, properties.getSecretToken());
                    }

                    final long startTime = System.currentTimeMillis();

                    return postReq.send()
                            // TODO Handle the response correctly
                            .onItem().apply(resp -> {
                                final long endTime = System.currentTimeMillis();
                                NotificationHistory history = new NotificationHistory();
                                history.setInvocationTime(endTime - startTime);
                                history.setEndpointId(endpoint.getId());
                                history.setTenant(endpoint.getTenant());

                                if(resp.statusCode() >= 200 && resp.statusCode() <= 300) {
                                    // Accepted
                                    history.setInvocationResult(true);
                                } else if(resp.statusCode() > 500) {
                                    // Temporary error, allow retry
                                    history.setInvocationResult(false);
                                } else {
                                    // Disable the target endpoint, it's not working correctly for us (such as 400)
                                    // must eb manually re-enabled
                                    // Redirects etc should have been followed by the vertx (test this)
                                    history.setInvocationResult(false);
                                }

                                if(!history.isInvocationResult()) {
                                    JsonObject details = new JsonObject();
                                    details.put("url", properties.getUrl());
                                    details.put("method", properties.getMethod());
                                    details.put("code", resp.statusCode());
                                    // This isn't async body reading, lets hope vertx handles it async underneath before calling this apply method
                                    details.put("response_body", resp.bodyAsString());
                                    history.setDetails(details);
                                }

                                return history;
                            })
                            .onFailure().recoverWithItem(t -> {
                                // TODO Duplicate code with the success part
                                final long endTime = System.currentTimeMillis();
                                NotificationHistory history = new NotificationHistory();
                                history.setInvocationTime(endTime - startTime);
                                history.setEndpointId(endpoint.getId());
                                history.setTenant(endpoint.getTenant());

                                // TODO Duplicate code with the error return code part
                                JsonObject details = new JsonObject();
                                details.put("url", properties.getUrl());
                                details.put("method", properties.getMethod());
                                details.put("error_message", t.getMessage());
                                history.setDetails(details);

                                System.out.printf("We failed to process the request: %s\n", t.getMessage());
                                if(t instanceof ConnectException) {
                                    // Connection refused for example
                                    ConnectException ce = (ConnectException) t;
                                }
                                else if(t instanceof UnknownHostException) {
                                    UnknownHostException uhe = (UnknownHostException) t;
                                }
                                // TODO Create NotificationHistory here also!
                                // io.netty.channel.ConnectTimeoutException: connection timed out: webhook.site/46.4.105.116:443
                                return history;
                            })
                            .onItem().produceUni(history -> notifResources.createNotificationHistory(history));
                })
                .merge()
                .onFailure().invoke(Throwable::printStackTrace)
                .toUni().map(ignored -> null);
    }
}
