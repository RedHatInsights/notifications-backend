package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class EmailSubscriptionTypeProcessorTest {

    EmailSubscriptionTypeProcessor testee = new EmailSubscriptionTypeProcessor();

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
}
