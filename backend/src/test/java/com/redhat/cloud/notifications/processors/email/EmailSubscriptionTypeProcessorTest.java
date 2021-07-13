package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.templates.EmailTemplateFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.mutiny.Multi;
import io.vertx.mutiny.ext.web.client.WebClient;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class EmailSubscriptionTypeProcessorTest {

    EmailSubscriptionTypeProcessor testee;

    @InjectMock
    EmailTemplateFactory emailTemplateFactory;

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
    void name() {
        final Action action = new Action("someBundle", "someApplication", "someEventType", LocalDateTime.now(), "someAccountId", Map.of(), List.of());

        Endpoint endpoint = new Endpoint();
        endpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);
        endpoint.setName("someName");
        endpoint.setEnabled(true);
        endpoint.setDescription("someDescription");
        endpoint.setAccountId("someAccountId");

        final Multi<NotificationHistory> process = testee.process(action, List.of(endpoint));
    }

    @Test
    void shouldSendEmail() {
        Mockito.mock(WebClient.class);
        testee.sendEmail(new Notification(new Action(),new Endpoint()), EmailSubscriptionType.DAILY);
    }
}
