package com.redhat.cloud.notifications.processors.webhooks.transformers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.ingress.Action;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BaseTransformer {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonObject createMessage(Action action) {
        JsonObject message = new JsonObject(action.getPayload());
        return message;
    }

    public Uni<JsonObject> transform(Action action) {
        // Fields and terminology straight from the target project
        JsonObject message = new JsonObject();
        message.put("application", action.getApplication());
        message.put("account_id", action.getAccountId());
        message.put("timestamp", action.getTimestamp().toString());
        message.put("message", createMessage(action));

        return Uni.createFrom().item(message);
    }
}
