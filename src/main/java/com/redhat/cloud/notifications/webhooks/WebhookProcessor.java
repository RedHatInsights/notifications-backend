package com.redhat.cloud.notifications.webhooks;

import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.function.Function;

@ApplicationScoped
public class WebhookProcessor {

    private final static String TOKEN_HEADER = "X-Insight-Token";

    @Inject
    Vertx vertx;

    @Inject
    EndpointResources resources;

    // TODO This process(Uni<Notification>) should probably be an interface that can be fetched with injections (like action plugins in policies-engine)
    public Uni<Void> process(Notification item) {
        return resources.getEndpoints(item.getTenant())
                .onItem().produceUni(endpoint -> {
                    WebhookAttributes properties = (WebhookAttributes) endpoint.getProperties();

                    // TODO Add here the method type as the call
                    HttpRequest<Buffer> postReq = WebClient.create(vertx)
                            .post(properties.getUrl());

                    if (properties.getSecretToken() != null && !properties.getSecretToken().isBlank()) {
                        postReq = postReq.putHeader(TOKEN_HEADER, properties.getSecretToken());

                    }

                    return postReq.send()
                            // TODO Handle the response correctly
                            .onFailure().invoke(t -> {
                                // TODO Handle timeouts and such here (or incorrect DNS etc)
                                // Make at least a rudimentary detect when to disable and when to retry later
                                System.out.printf("We failed to process the request: %s\n", t.getMessage());
                            })
                            .onItem().apply((Function<HttpResponse<Buffer>, Void>) resp -> {
                                if(resp.statusCode() >= 200 && resp.statusCode() <= 300) {
                                    // Accepted
                                }
                                if(resp.statusCode() >= 400 && resp.statusCode() < 500) {
                                    // Disable, this isn't temporary error
                                } else if(resp.statusCode() > 500) {
                                    // Temporary error, allow retry
                                } else {
                                    // Redirects etc should have been followed by the vertx (test this)
                                }

                                return null;
                            });
                }).merge().toUni();
    }
}
