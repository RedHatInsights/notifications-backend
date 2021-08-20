package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.AggregationCommand;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusTestResource(TestLifecycleManager.class)
class DailyEmailAggregationJobTest {

    @Inject
    EmailAggregationResources emailAggregationResources;

    DailyEmailAggregationJob dailyEmailAggregationJob;

    @Inject
    ResourceHelpers helpers;

    @BeforeAll
    void init() {
        dailyEmailAggregationJob = new DailyEmailAggregationJob();
        dailyEmailAggregationJob.emailAggregationResources = emailAggregationResources;
    }

    @Test
    void testEmailSubscriptionDaily() {
        final String tenant1 = "tenant1";
        final String tenant2 = "tenant2";
        final String noSubscribedUsersTenant = "tenant3";

        final String bundle = "rhel";
        final String application = "policies";

        try {
            helpers.createSubscription(tenant1, "foo", bundle, application, DAILY);
            helpers.createSubscription(tenant1, "bar", bundle, application, DAILY);
            helpers.createSubscription(tenant1, "admin", bundle, application, DAILY);

            helpers.createSubscription(tenant2, "baz", bundle, application, DAILY);
            helpers.createSubscription(tenant2, "bar", bundle, application, DAILY);

            helpers.removeSubscription(noSubscribedUsersTenant, "test", bundle, application, DAILY);

            // applications without template or aggregations do not break the process
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-01");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-02", "hostid-02");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-03", "hostid-03");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-04");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-05");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-06");
            helpers.addEmailAggregation(tenant1, bundle, "unknown-application", "policyid-01", "hostid-06");
            helpers.addEmailAggregation(tenant1, "unknown-bundle", application, "policyid-01", "hostid-06");
            helpers.addEmailAggregation(tenant1, "unknown-bundle", "unknown-application", "policyid-01", "hostid-06");

            final List<AggregationCommand> emailAggregations1 = dailyEmailAggregationJob.processAggregateEmails(LocalDateTime.now());

            // 4 aggregationCommands
            assertEquals(4, emailAggregations1.size());

            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-01");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-02", "hostid-02");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-03", "hostid-03");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-04");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-05");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-06");

            helpers.addEmailAggregation(tenant2, bundle, application, "policyid-11", "hostid-11");
            helpers.addEmailAggregation(tenant2, bundle, application, "policyid-11", "hostid-15");
            helpers.addEmailAggregation(tenant2, bundle, application, "policyid-11", "hostid-16");

            helpers.addEmailAggregation(noSubscribedUsersTenant, bundle, application, "policyid-21", "hostid-21");
            helpers.addEmailAggregation(noSubscribedUsersTenant, bundle, application, "policyid-21", "hostid-25");
            helpers.addEmailAggregation(noSubscribedUsersTenant, bundle, application, "policyid-21", "hostid-26");

            final List<AggregationCommand> emailAggregations2 = dailyEmailAggregationJob.processAggregateEmails(LocalDateTime.now());
            // 6 aggregationCommands, 2 new (tenant2, bundle, application) and (noSubscribed, bundle, application)
            assertEquals(6, emailAggregations2.size());

            helpers.createSubscription(noSubscribedUsersTenant, "test", bundle, application, DAILY);
            final List<AggregationCommand> emailAggregations3 = dailyEmailAggregationJob.processAggregateEmails(LocalDateTime.now());
            // still 6 - subscription not taken into account in this process
            assertEquals(6, emailAggregations3.size());
        } finally {
            helpers.purgeAggregations();
        }
    }
}
