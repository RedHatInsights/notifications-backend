package com.redhat.cloud.notifications.processors.pagerduty;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.models.PagerDutyProperties;
import com.redhat.cloud.notifications.models.PagerDutySeverity;
import com.redhat.cloud.notifications.processors.InsightsUrlsBuilder;
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

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.processors.ConnectorSender.TOCAMEL_CHANNEL;
import static com.redhat.cloud.notifications.processors.pagerduty.PagerDutyProcessor.PROCESSED_PAGERDUTY_COUNTER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class PagerDutyProcessorTest {

    @Inject
    PagerDutyProcessor pagerDutyProcessor;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @InjectMock
    EngineConfig engineConfig;

    @InjectMock
    NotificationHistoryRepository notificationHistoryRepository;

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    private InMemorySink<JsonObject> inMemorySink;

    @Inject
    BaseTransformer transformer;

    @Inject
    InsightsUrlsBuilder insightsUrlsBuilder;

    @PostConstruct
    void postConstruct() {
        inMemorySink = inMemoryConnector.sink(TOCAMEL_CHANNEL);
    }

    @BeforeEach
    void beforeEach() {
        inMemorySink.clear();
        micrometerAssertionHelper.saveCounterValuesBeforeTest(PROCESSED_PAGERDUTY_COUNTER);
    }

    @AfterEach
    void afterEach() {
        micrometerAssertionHelper.clearSavedValues();
    }

    @Test
    void testPagerDutyUsingConnector() {
        // Make sure that the payload does not get stored in the database.
        when(engineConfig.getKafkaToCamelMaximumRequestSize()).thenReturn(Integer.MAX_VALUE);

        Action pagerDutyActionMessage = buildPagerDutyAction();
        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(pagerDutyActionMessage));
        event.setApplicationDisplayName("policies");

        Application application = new Application();
        application.setName("policies");
        EventType eventType = new EventType();
        eventType.setApplication(application);
        eventType.setName("policy-triggered");
        event.setEventType(eventType);
        event.setOrgId(DEFAULT_ORG_ID);
        Endpoint ep = buildPagerDutyEndpoint();

        pagerDutyProcessor.process(event, List.of(ep));
        ArgumentCaptor<NotificationHistory> historyArgumentCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(notificationHistoryRepository, times(1)).createNotificationHistory(historyArgumentCaptor.capture());
        NotificationHistory history = historyArgumentCaptor.getAllValues().getFirst();
        assertFalse(history.isInvocationResult());
        assertEquals(NotificationStatus.PROCESSING, history.getStatus());
        // Now let's check the Kafka messages sent to the outgoing channel.
        // The channel should have received two messages.
        assertEquals(1, inMemorySink.received().size());

        // We'll only check the payload and metadata of the first Kafka message.
        Message<JsonObject> message = inMemorySink.received().getFirst();
        JsonObject payload = message.getPayload();

        final JsonObject payloadToSend = transformer.toJsonObject(event);
        insightsUrlsBuilder.buildInventoryUrl(payloadToSend, ep.getType().name().toLowerCase())
                .ifPresent(url -> payloadToSend.put("inventory_url", url));
        payloadToSend.put("application_url", insightsUrlsBuilder.buildApplicationUrl(payloadToSend, ep.getType().name().toLowerCase()));
        payloadToSend.put("severity", PagerDutySeverity.ERROR);
        assertEquals(payloadToSend, payload.getJsonObject("payload"));

        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_PAGERDUTY_COUNTER, 1);
    }

    @Test
    void testPagerdutyUsingIqeMessage() {
        // Make sure that the payload does not get stored in the database.
        when(engineConfig.getKafkaToCamelMaximumRequestSize()).thenReturn(Integer.MAX_VALUE);

        Action pagerDutyActionMessage = new Action();
        pagerDutyActionMessage.setBundle("rhel");
        pagerDutyActionMessage.setApplication("inventory");
        pagerDutyActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        pagerDutyActionMessage.setEventType("new-system-registered");
        pagerDutyActionMessage.setAccountId(DEFAULT_ACCOUNT_ID);
        pagerDutyActionMessage.setOrgId(DEFAULT_ORG_ID);

        Context context = new Context.ContextBuilder()
                .withAdditionalProperty("inventory_id", "85094ed1-1c52-4bc5-8e3e-4ea3869a17ce")
                .withAdditionalProperty("hostname", "rhiqe.2349fj.notif-test")
                .withAdditionalProperty("display_name", "rhiqe.2349fj.notif-test")
                .withAdditionalProperty("rhel_version", "8.0")
                .build();
        pagerDutyActionMessage.setContext(context);

        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(pagerDutyActionMessage));
        event.setBundleDisplayName("Red Hat Enterprise Linux");
        event.setApplicationDisplayName("Inventory");
        event.setEventTypeDisplayName("New system registered");

        Application application = new Application();
        application.setName("inventory");
        application.setDisplayName("Inventory");
        EventType eventType = new EventType();
        eventType.setApplication(application);
        eventType.setName("new-system-registered");
        eventType.setDisplayName("New system registered");
        event.setEventType(eventType);

        Endpoint ep = buildPagerDutyEndpoint();

        pagerDutyProcessor.process(event, List.of(ep));
        ArgumentCaptor<NotificationHistory> historyArgumentCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(notificationHistoryRepository, times(1)).createNotificationHistory(historyArgumentCaptor.capture());
        NotificationHistory history = historyArgumentCaptor.getAllValues().getFirst();
        assertFalse(history.isInvocationResult());
        assertEquals(NotificationStatus.PROCESSING, history.getStatus());
        // Now let's check the Kafka messages sent to the outgoing channel.
        // The channel should have received two messages.
        assertEquals(1, inMemorySink.received().size());

        // We'll only check the payload and metadata of the first Kafka message.
        Message<JsonObject> message = inMemorySink.received().getFirst();
        JsonObject payload = message.getPayload();

        final JsonObject payloadToSend = transformer.toJsonObject(event);
        insightsUrlsBuilder.buildInventoryUrl(payloadToSend, ep.getType().name())
                .ifPresent(url -> payloadToSend.put("inventory_url", url));
        payloadToSend.put("application_url", insightsUrlsBuilder.buildApplicationUrl(payloadToSend, ep.getType().name()));
        payloadToSend.put("severity", PagerDutySeverity.ERROR);
        assertEquals(payloadToSend, payload.getJsonObject("payload"));

        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_PAGERDUTY_COUNTER, 1);
    }

    @Test
    void testEmailsOnlyMode() {
        when(engineConfig.isEmailsOnlyModeEnabled()).thenReturn(true);

        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(buildPagerDutyAction()));

        pagerDutyProcessor.process(event, List.of(new Endpoint()));
        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_PAGERDUTY_COUNTER, 0);
    }

    private static Action buildPagerDutyAction() {
        Action pagerDutyActionMessage = new Action();
        pagerDutyActionMessage.setBundle("mybundle");
        pagerDutyActionMessage.setApplication("PagerDutyTest");
        pagerDutyActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        pagerDutyActionMessage.setEventType("testPagerDuty");
        pagerDutyActionMessage.setAccountId("tenant");
        pagerDutyActionMessage.setOrgId(DEFAULT_ORG_ID);

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

        pagerDutyActionMessage.setEvents(
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

        pagerDutyActionMessage.setContext(context);
        return pagerDutyActionMessage;
    }

    private static Endpoint buildPagerDutyEndpoint() {
        PagerDutyProperties properties = new PagerDutyProperties();
        properties.setSeverity(PagerDutySeverity.ERROR);

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
