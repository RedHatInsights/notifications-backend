package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor.AGGREGATION_CHANNEL;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
class EmailSubscriptionTypeProcessorTest {

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

        assertEquals(0, process.collect().asList().await().indefinitely().size());
    }

    @Test
    void shouldNotProcessWhenEndpointsAreEmpty() {
        final Action action = new Action("someBundle", "someApplication", "someEventType", LocalDateTime.now(), "someAccountId", Map.of(), List.of());
        final Multi<NotificationHistory> process = testee.process(action, List.of());

        assertEquals(0, process.collect().asList().await().indefinitely().size());
    }

    @Test
    @Disabled
    void shouldProcess() {
        final Action action = new Action("someBundle", "someApplication", "someEventType", LocalDateTime.now(), "someAccountId", Map.of(), List.of());

        Endpoint endpoint = new Endpoint();
        endpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);
        endpoint.setName("someName");
        endpoint.setEnabled(true);
        endpoint.setDescription("someDescription");
        endpoint.setAccountId("someAccountId");

        testee.process(action, List.of(endpoint));
    }

    @Test
    @Disabled
    void shouldSendEmail() {
        List<EmailSubscription> emailSubscriptions = new LinkedList<>();
        final EmailSubscription emailSubscription = new EmailSubscription();
        emailSubscription.setApplication(new Application());
        emailSubscriptions.add(emailSubscription);
        Uni<List<EmailSubscription>> listUni = Uni.createFrom().item(emailSubscriptions);

        final EndpointEmailSubscriptionResources endpointEmailSubscriptionResources = mock(EndpointEmailSubscriptionResources.class);
        when(endpointEmailSubscriptionResources.getEmailSubscribers(anyString(), anyString(), anyString(), any())).thenReturn(listUni);

        final WebhookTypeProcessor webhookTypeProcessor = spy(WebhookTypeProcessor.class);

        testee.sendEmail(new Notification(new Action(), new Endpoint()), DAILY).await().indefinitely();

        verify(webhookTypeProcessor, times(1)).doHttpRequest(any(), any(), any());
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
}
