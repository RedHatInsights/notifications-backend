package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor.AGGREGATION_CHANNEL;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@QuarkusTest
class EmailSubscriptionTypeProcessorTest extends DbIsolatedTest {

    @Inject
    EmailSubscriptionTypeProcessor testee;

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @InjectMock
    EndpointEmailSubscriptionResources subscriptionResources;

    @Test
    void shouldNotProcessWhenEndpointsAreNull() {
        final Action action = new Action("someBundle", "someApplication", "someEventType", LocalDateTime.now(), "someAccountId", Map.of(), List.of());
        final Multi<NotificationHistory> process = testee.process(action, null);

        process.subscribe()
                .withSubscriber(AssertSubscriber.create())
                .assertCompleted()
                .assertHasNotReceivedAnyItem();
    }

    @Test
    void shouldNotProcessWhenEndpointsAreEmpty() {
        final Action action = new Action("someBundle", "someApplication", "someEventType", LocalDateTime.now(), "someAccountId", Map.of(), List.of());
        final Multi<NotificationHistory> process = testee.process(action, List.of());

        process.subscribe()
                .withSubscriber(AssertSubscriber.create())
                .assertCompleted()
                .assertHasNotReceivedAnyItem();
    }

    @Test
    void shouldSuccessfullySendEmail() {
        EmailAggregation aggregation1 = buildEmailAggregation("account-1", "bundle-1", "app-1");
        EmailAggregation aggregation2 = buildEmailAggregation("account-2", "bundle-2", "app-2");
        List<EmailAggregation> aggregations = List.of(aggregation1, aggregation2);
        String testPayload = Json.encode(aggregations);
        inMemoryConnector.source(AGGREGATION_CHANNEL).send(testPayload);

        // Let's check that EndpointEmailSubscriptionResources#sendEmail was called for each aggregation.
        verify(subscriptionResources, times(1)).getEmailSubscribers(eq(aggregation1.getAccountId()), eq(aggregation1.getBundleName()), eq(aggregation1.getApplicationName()), eq(DAILY));
        verify(subscriptionResources, times(1)).getEmailSubscribers(eq(aggregation2.getAccountId()), eq(aggregation2.getBundleName()), eq(aggregation2.getApplicationName()), eq(DAILY));
        verifyNoMoreInteractions(subscriptionResources);
    }

    private static EmailAggregation buildEmailAggregation(String accountId, String bundleName, String appName) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setAccountId(accountId);
        aggregation.setBundleName(bundleName);
        aggregation.setApplicationName(appName);
        aggregation.setPayload(new JsonObject());
        aggregation.setCreated(LocalDateTime.now(ZoneOffset.UTC));
        return aggregation;
    }

    @Test
    void consumeEmailAggregationsShouldNotThrowInCaseOfInvalidPayload() {
        Uni<Void> consumeEmailAggregations = testee.consumeEmailAggregations("I am not valid!");

        consumeEmailAggregations.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .assertCompleted()
                .assertItem(null);
    }
}
