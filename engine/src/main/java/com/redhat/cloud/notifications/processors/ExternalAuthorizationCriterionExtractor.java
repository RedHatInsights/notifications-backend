package com.redhat.cloud.notifications.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ExternalAuthorizationCriterionExtractor {

    public static final String EXTERNAL_AUTHORIZATION_CRITERIA = "recipients_authorization_criterion";

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    ObjectMapper objectMapper;

    public ExternalAuthorizationCriterion extract(Event event) {
        return extract(baseTransformer.toJsonObject(event));
    }

    public ExternalAuthorizationCriterion extract(EmailAggregation emailAggregation) {
        return extract(emailAggregation.getPayload());
    }

    private ExternalAuthorizationCriterion extract(JsonObject data) {
        if (null != data.getJsonObject(BaseTransformer.CONTEXT) && null != data.getJsonObject(BaseTransformer.CONTEXT).getJsonObject(EXTERNAL_AUTHORIZATION_CRITERIA)) {
            try {
                return objectMapper.convertValue(data.getJsonObject(BaseTransformer.CONTEXT).getJsonObject(EXTERNAL_AUTHORIZATION_CRITERIA), ExternalAuthorizationCriterion.class);
            } catch (IllegalArgumentException e) {
                Log.error("Error parsing authorization criteria", e);
            }
        }
        return null;
    }
}
