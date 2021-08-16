package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import java.util.List;

public class DummyEndpointTypeProcessor implements EndpointTypeProcessor {

    @Override
    public Multi<NotificationHistory> process(Action action, List<Endpoint> endpoints) {
        return null;
    }

    @Override
    public Uni<JsonObject> transform(Action action) {
        return EndpointTypeProcessor.super.transform(action);
    }
}
