package com.redhat.cloud.notifications.processors.webhook;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.processors.ConnectorSender.TOCAMEL_CHANNEL;
import static com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor.PROCESSED_WEBHOOK_COUNTER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class WebhookTest {

    @Inject
    WebhookTypeProcessor webhookTypeProcessor;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @Inject
    FeatureFlipper featureFlipper;

    @InjectMock
    NotificationHistoryRepository notificationHistoryRepository;

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    private InMemorySink<JsonObject> inMemorySink;

    @Inject
    BaseTransformer transformer;

    @PostConstruct
    void postConstruct() {
        inMemorySink = inMemoryConnector.sink(TOCAMEL_CHANNEL);
    }

    @BeforeEach
    void beforeEach() {
        inMemorySink.clear();
        micrometerAssertionHelper.saveCounterValuesBeforeTest(PROCESSED_WEBHOOK_COUNTER);
    }

    @AfterEach
    void afterEach() {
        micrometerAssertionHelper.clearSavedValues();
    }

    @Test
    void testWebhookUsingConnector() {
        String testUrl = "https://my.webhook.connector.com";
        Action webhookActionMessage = buildWebhookAction();
        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(webhookActionMessage));
        Endpoint ep = buildWebhookEndpoint("https://my.webhook.connector.com");

        webhookTypeProcessor.process(event, List.of(ep));
        ArgumentCaptor<NotificationHistory> historyArgumentCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(notificationHistoryRepository, times(1)).createNotificationHistory(historyArgumentCaptor.capture());
        NotificationHistory history = historyArgumentCaptor.getAllValues().get(0);
        assertFalse(history.isInvocationResult());
        assertEquals(NotificationStatus.PROCESSING, history.getStatus());
        // Now let's check the Kafka messages sent to the outgoing channel.
        // The channel should have received two messages.
        assertEquals(1, inMemorySink.received().size());

        // We'll only check the payload and metadata of the first Kafka message.
        Message<JsonObject> message = inMemorySink.received().get(0);
        JsonObject payload = message.getPayload();
        assertEquals(testUrl, payload.getJsonObject("endpoint_properties").getString("url"));

        final JsonObject payloadToSent = transformer.toJsonObject(event);
        assertEquals(payloadToSent, payload.getJsonObject("payload"));

        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_WEBHOOK_COUNTER, 1);
    }

    @Test
    void testEmailsOnlyMode() {
        featureFlipper.setEmailsOnlyMode(true);
        try {

            Event event = new Event();
            event.setEventWrapper(new EventWrapperAction(buildWebhookAction()));

            webhookTypeProcessor.process(event, List.of(new Endpoint()));
            micrometerAssertionHelper.assertCounterIncrement(PROCESSED_WEBHOOK_COUNTER, 0);

        } finally {
            featureFlipper.setEmailsOnlyMode(false);
        }
    }

    private static Action buildWebhookAction() {
        Action webhookActionMessage = new Action();
        webhookActionMessage.setBundle("mybundle");
        webhookActionMessage.setApplication("WebhookTest");
        webhookActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        webhookActionMessage.setEventType("testWebhook");
        webhookActionMessage.setAccountId("tenant");
        webhookActionMessage.setOrgId(DEFAULT_ORG_ID);

        Payload payload1 = new Payload.PayloadBuilder()
                .withAdditionalProperty("any", "thing")
                .withAdditionalProperty("we", 1)
                .withAdditionalProperty("want", "here")
                .build();

        Context context = new Context.ContextBuilder()
                .withAdditionalProperty("free", "more")
                .withAdditionalProperty("format", 1)
                .withAdditionalProperty("here", "stuff")
                .build();

        webhookActionMessage.setEvents(
                List.of(
                        new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                                .withMetadata(new Metadata.MetadataBuilder().build())
                                .withPayload(payload1)
                                .build(),
                        new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                                .withMetadata(new Metadata.MetadataBuilder().build())
                                .withPayload(new Payload.PayloadBuilder().build())
                                .build()
                )
        );

        webhookActionMessage.setContext(context);

        return webhookActionMessage;
    }

    private static Endpoint buildWebhookEndpoint(String url) {
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(HttpType.POST);
        properties.setUrl(url);

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("positive feeling");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);
        ep.setCreated(LocalDateTime.now());

        return ep;
    }
}
