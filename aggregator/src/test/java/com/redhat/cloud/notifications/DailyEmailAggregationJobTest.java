package com.redhat.cloud.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.models.AggregationCommand;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import javax.enterprise.inject.Any;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusTestResource(TestLifecycleManager.class)
class DailyEmailAggregationJobTest {

    @Inject
    ResourceHelpers helpers;

    @Inject
    DailyEmailAggregationJob testee;

    @Inject
    @Any
    InMemoryConnector connector;

    @BeforeEach
    void setUp() {
        helpers.purgeEmailAggregations();
    }

    @Test
    @TestTransaction
    void shouldNotProcessMailsWhenNewCronJobIsDisabledByEnvironmentVariable() {
        System.setProperty("notifications.aggregator.email.subscription.periodic.cron.enabled", "false");

        final EmailAggregationResources emailAggregationResources = mock(EmailAggregationResources.class);
        final ObjectMapper objectMapper = mock(ObjectMapper.class);
        DailyEmailAggregationJob dailyEmailAggregationJob = new DailyEmailAggregationJob(emailAggregationResources, objectMapper);

        dailyEmailAggregationJob.processDailyEmail();
        verifyNoInteractions(emailAggregationResources);
    }

    @Test
    @TestTransaction
    void shouldProcessZeroAggregations() {
        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now(UTC));

        assertEquals(0, emailAggregations.size());
    }
}
