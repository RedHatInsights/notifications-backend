package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@QuarkusTest
class EmailSubscriptionTypeProcessorTest extends DbIsolatedTest {

    @Inject
    EmailSubscriptionTypeProcessor testee;

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
