package com.redhat.cloud.notifications.processors.webhooks.transformers;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BaseTransformer {

    private JsonObject createMessage(Action action) {
        JsonObject message = new JsonObject();

        Context context = action.getEvent();
        context.getMessage().forEach(message::put);

        return message;
    }

    public Uni<String> transform(Action action) {
        // Fields and terminology straight from the target project
        JsonObject message = new JsonObject();
        message.put("application", action.getApplication());
        message.put("account_id", action.getEvent().getAccountId());
        message.put("timestamp", action.getTimestamp().toString());
        message.put("message", createMessage(action));

        return Uni.createFrom().item(message.encode());
    }
}
