package com.redhat.cloud.notifications.webhooks;

import com.redhat.cloud.notifications.db.EndpointResourcesJDBC;
import com.redhat.cloud.notifications.db.NotificationResources;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.function.Function;

@ApplicationScoped
public class WebhookProcessor {

    private final static String TOKEN_HEADER = "X-Insight-Token";

    @Inject
    Vertx vertx;

    @Inject
    EndpointResourcesJDBC resources;

    @Inject
    NotificationResources notifResources;

    // TODO This process(Uni<Notification>) should probably be an interface that can be fetched with injections (like action plugins in policies-engine)
    public Uni<Void> process(Notification item) {
        return resources.getActiveEndpointsPerType(item.getTenant(), Endpoint.EndpointType.WEBHOOK)
                .onItem().produceUni(endpoint -> {
                    System.out.printf("Processing webhook: %s\n", endpoint.toString());
                    WebhookAttributes properties = (WebhookAttributes) endpoint.getProperties();

                    WebClientOptions options = new WebClientOptions()
                            .setSsl(!properties.isDisableSSLVerification())
                            .setConnectTimeout(3000);

                    // TODO Add here the method type as the call
                    HttpRequest<Buffer> postReq = WebClient.create(vertx, options)
                            .postAbs(properties.getUrl());

                    if (properties.getSecretToken() != null && !properties.getSecretToken().isBlank()) {
                        postReq = postReq.putHeader(TOKEN_HEADER, properties.getSecretToken());

                    }

                    return postReq.send()
                            // TODO Handle the response correctly
                            .onFailure().invoke(t -> {
                                // TODO Handle timeouts and such here (or incorrect DNS etc)
                                // Make at least a rudimentary detect when to disable and when to retry later
                                System.out.printf("We failed to process the request: %s\n", t.getMessage());
                                if(t instanceof ConnectException) {
                                    // Connection refused for example
                                    ConnectException ce = (ConnectException) t;
                                }
                                else if(t instanceof UnknownHostException) {
                                    UnknownHostException uhe = (UnknownHostException) t;
                                }
                                // io.netty.channel.ConnectTimeoutException: connection timed out: webhook.site/46.4.105.116:443
                            })
                            .onItem().produceUni(resp -> {
                                if(resp.statusCode() >= 200 && resp.statusCode() <= 300) {
                                    // Accepted
                                    NotificationHistory history = new NotificationHistory();
                                    history.setInvocationTime(0); // TODO Add instrumentation to vertx client
                                    history.setInvocationResult(true);
                                    history.setEndpointId(endpoint.getId());
                                    history.setTenant(endpoint.getTenant());

                                    return notifResources.createNotificationHistory(history);
                                } else if(resp.statusCode() > 500) {
                                    // Temporary error, allow retry
                                } else {
                                    // Redirects etc should have been followed by the vertx (test this)
                                }

                                return Uni.createFrom().nullItem();
                            });
//                            .onItem().apply((Function<HttpResponse<Buffer>, Void>) resp -> {
//                                if(resp.statusCode() >= 200 && resp.statusCode() <= 300) {
//                                    // Accepted
//                                    NotificationHistory history = new NotificationHistory();
//                                    history.setInvocationTime(0); // TODO Add instrumentation to vertx client
//                                    history.setInvocationResult(true);
//                                    history.setEndpointId(endpoint.getId());
//                                    history.setTenant(endpoint.getTenant());
//
//                                    notifResources.createNotificationHistory(history);
//                                }
//                                if(resp.statusCode() >= 400 && resp.statusCode() < 500) {
//                                    // Disable, this isn't temporary error
//                                } else if(resp.statusCode() > 500) {
//                                    // Temporary error, allow retry
//                                } else {
//                                    // Redirects etc should have been followed by the vertx (test this)
//                                }
//
//                                return null;
//                            });
                }).merge().toUni();
    }
}
