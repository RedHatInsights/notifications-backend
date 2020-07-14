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
                                System.out.printf("We failed to process the request: %s\n", t.getMessage());
                            })
                            .onItem().apply((Function<HttpResponse<Buffer>, Void>) bufferHttpResponse -> null);
                }).merge().toUni();
    }
}
