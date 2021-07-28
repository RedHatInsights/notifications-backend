package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.templates.EmailTemplateFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;
import org.junit.jupiter.api.Test;

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@QuarkusTest
class EmailSubscriptionTypeProcessorTest extends DbIsolatedTest {

    @Inject
    EmailSubscriptionTypeProcessor testee;

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @InjectMock
    EmailTemplateFactory emailTemplateFactory;

    @InjectMock
    EmailAggregationResources emailAggregationResources;

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

}
