package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.models.EmailAggregation;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class ResourceHelpers {

    @Inject
    EmailAggregationRepository emailAggregationRepository;

    public Uni<Boolean> addEmailAggregation(String accountId, String bundleName, String applicationName, JsonObject payload) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setAccountId(accountId);
        aggregation.setBundleName(bundleName);
        aggregation.setApplicationName(applicationName);
        aggregation.setPayload(payload);
        return emailAggregationRepository.addEmailAggregation(aggregation);
    }
}
