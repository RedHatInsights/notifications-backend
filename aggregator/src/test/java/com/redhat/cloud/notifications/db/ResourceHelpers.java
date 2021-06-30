package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class ResourceHelpers {

    @Inject
    EndpointEmailSubscriptionResources subscriptionResources;

    @Inject
    EmailAggregationResources emailAggregationResources;

    public void createSubscription(String tenant, String username, String bundle, String application, EmailSubscriptionType type) {
        subscriptionResources.subscribe(tenant, username, bundle, application, type);
    }

    public void removeSubscription(String tenant, String username, String bundle, String application, EmailSubscriptionType type) {
        subscriptionResources.unsubscribe(tenant, username, bundle, application, type);
    }

    public void addEmailAggregation(String tenant, String bundle, String application, String policyId, String insightsId) {
        EmailAggregation aggregation = TestHelpers.createEmailAggregation(tenant, bundle, application, policyId, insightsId);
        emailAggregationResources.addEmailAggregation(aggregation);
    }
}
