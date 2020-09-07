package com.redhat.cloud.notifications.webhooks.transformers;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.EventConditionEval;
import org.hawkular.alerts.api.model.trigger.Trigger;

import javax.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

@ApplicationScoped
public class PoliciesTransformer {

    private static String APPLICATION_NAME = "policies";

    private JsonObject createMessage(Action action) {
        JsonObject message = new JsonObject();

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
                    message.put("display_name", eventEval.getValue().getTags().get("display_name"));
                    break Outer; // We only want to process the first one
                }
            }
        }

        return message;
    }

    public Uni<Object> transform(Action action) {
        // Fields and terminology straight from the target project
        LocalDateTime ts = Instant.ofEpochMilli(action.getCtime()).atZone(ZoneId.systemDefault()).toLocalDateTime();

        JsonObject message = new JsonObject();
        message.put("application", APPLICATION_NAME);
        message.put("account_id", action.getTenantId());
        message.put("timestamp", ts.toString());
        message.put("message", createMessage(action));

        return Uni.createFrom().item(message);
    }
}
