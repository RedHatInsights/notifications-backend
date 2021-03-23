package com.redhat.cloud.notifications.migration.policynotification;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

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
