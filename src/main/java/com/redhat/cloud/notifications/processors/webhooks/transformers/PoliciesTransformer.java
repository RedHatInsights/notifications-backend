package com.redhat.cloud.notifications.processors.webhooks.transformers;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PoliciesTransformer {

    private JsonObject createMessage(Action action) {
        JsonObject message = new JsonObject();

        Context context = action.getEvent();
        context.getMessage().forEach(message::put);
/*
        Trigger trigger = action.getEvent().getTrigger();
        message.put("policy_id", trigger.getId());
        message.put("policy_name", trigger.getName());
        message.put("policy_description", trigger.getDescription());

        Outer:
        for (Set<ConditionEval> evalSet : action.getEvent().getEvalSets()) {
            for (ConditionEval conditionEval : evalSet) {
                if (conditionEval instanceof EventConditionEval) {
                    EventConditionEval eventEval = (EventConditionEval) conditionEval;
                    message.put("policy_condition", eventEval.getCondition().getExpression());

                    message.put("insights_id", eventEval.getContext().get("insights_id"));
                    message.put("display_name", eventEval.getValue().getTags().get("display_name").iterator().next());
                    break Outer; // We only want to process the first one
                }
            }
        }
*/
        return message;
    }

    public Uni<JsonObject> transform(Action action) {
        // Fields and terminology straight from the target project
        JsonObject message = new JsonObject();
        String APPLICATION_NAME = "policies";
        message.put("application", APPLICATION_NAME);
        message.put("account_id", action.getEvent().getAccountId());
        message.put("timestamp", action.getTimestamp().toString());
        message.put("message", createMessage(action));

        return Uni.createFrom().item(message);
    }
}
