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
public class ExternalAuthorizationCriteriaExtractor {

    public static final String EXTERNAL_AUTHORIZATION_CRITERIA = "authorization";

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    ObjectMapper objectMapper;

    public ExternalAuthorizationCriteria extract(Event event) {
        return extract(baseTransformer.toJsonObject(event));
    }

    public ExternalAuthorizationCriteria extract(EmailAggregation emailAggregation) {
        return extract(emailAggregation.getPayload());
    }

    private ExternalAuthorizationCriteria extract(JsonObject data) {
        if (null != data.getJsonObject(BaseTransformer.CONTEXT) && null != data.getJsonObject(BaseTransformer.CONTEXT).getJsonObject(EXTERNAL_AUTHORIZATION_CRITERIA)) {
            try {
                return objectMapper.convertValue(data.getJsonObject(BaseTransformer.CONTEXT).getJsonObject(EXTERNAL_AUTHORIZATION_CRITERIA), ExternalAuthorizationCriteria.class);
            } catch (IllegalArgumentException e) {
                Log.error("Error parsing authorization criteria", e);
            }
        }
        return null;
    }
}
