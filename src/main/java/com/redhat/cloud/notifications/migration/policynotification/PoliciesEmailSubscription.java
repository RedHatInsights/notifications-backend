package com.redhat.cloud.notifications.migration.policynotification;

import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
public class PoliciesEmailSubscription {
    public String accountId;
    public String eventType;
    public String userId;

    PoliciesEmailSubscription() {

    }

    PoliciesEmailSubscription(String accountId, String userId, String eventType) {
        this.accountId = accountId;
        this.eventType = eventType;
        this.userId = userId;
    }
}
