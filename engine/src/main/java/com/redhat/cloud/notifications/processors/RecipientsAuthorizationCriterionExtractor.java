package com.redhat.cloud.notifications.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.ingress.RecipientsAuthorizationCriterion;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static com.redhat.cloud.notifications.transformers.BaseTransformer.RECIPIENTS_AUTHORIZATION_CRITERION;

@ApplicationScoped
public class RecipientsAuthorizationCriterionExtractor {

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    ObjectMapper objectMapper;

    public RecipientsAuthorizationCriterion extract(Event event) {
        return extract(baseTransformer.toJsonObject(event));
    }

    public RecipientsAuthorizationCriterion extract(EmailAggregation emailAggregation) {
        return extract(emailAggregation.getPayload());
    }

    private RecipientsAuthorizationCriterion extract(JsonObject data) {
        if (null != data.getJsonObject(RECIPIENTS_AUTHORIZATION_CRITERION)) {
            try {
                return objectMapper.convertValue(data.getJsonObject(RECIPIENTS_AUTHORIZATION_CRITERION), RecipientsAuthorizationCriterion.class);
            } catch (IllegalArgumentException e) {
                Log.error("Error parsing authorization criteria", e);
            }
        }
        return null;
    }
}
