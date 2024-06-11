package com.redhat.cloud.notifications.processors.camel;

import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.IntegrationTemplate;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.Template;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.InjectMock;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static com.redhat.cloud.notifications.processors.ConnectorSender.CLOUD_EVENT_TYPE_PREFIX;
import static com.redhat.cloud.notifications.processors.ConnectorSender.TOCAMEL_CHANNEL;
import static com.redhat.cloud.notifications.processors.ConnectorSender.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public abstract class CamelProcessorTest {

    private static final String WEBHOOK_URL = "https://foo.bar";

    @InjectMock
    TemplateRepository templateRepository;

    @InjectMock
    NotificationHistoryRepository notificationHistoryRepository;

    @Inject
    MeterRegistry registry;

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    protected InMemorySink<JsonObject> inMemorySink;

    @PostConstruct
    void postConstruct() {
        inMemorySink = inMemoryConnector.sink(TOCAMEL_CHANNEL);
    }

    @BeforeEach
    @AfterEach
    void clearInMemorySink() {
        inMemorySink.clear();
    }

    protected abstract String getQuteTemplate();

    protected abstract String getExpectedMessage();

    protected abstract String getSubType();

    protected abstract CamelProcessor getCamelProcessor();

    @Test
    void testProcess() {
        mockTemplate();
        Event event = buildEvent();
        Endpoint endpoint = buildEndpoint();
        getCamelProcessor().process(event, List.of(endpoint));

        verify(templateRepository, times(1)).findIntegrationTemplate(any(), any(), any(), any(), any());
        verify(notificationHistoryRepository, times(1)).createNotificationHistory(any(NotificationHistory.class));
        verifyKafkaMessage();
    }

    protected void verifyKafkaMessage() {

        await().until(() -> inMemorySink.received().size() == 1);
        Message<JsonObject> message = inMemorySink.received().get(0);

        assertNotificationsConnectorHeader(message);

        CloudEventMetadata cloudEventMetadata = message.getMetadata(CloudEventMetadata.class).get();
        assertNotNull(cloudEventMetadata.getId());
        assertEquals(getExpectedCloudEventType(), cloudEventMetadata.getType());

        JsonObject notification = message.getPayload();

        assertEquals(DEFAULT_ORG_ID, notification.getString("org_id"));
        assertEquals(WEBHOOK_URL, notification.getString("webhookUrl"));
        assertEquals(getExpectedMessage(), notification.getString("message"));
    }

    protected void assertNotificationsConnectorHeader(Message<JsonObject> message) {
        byte[] actualConnectorHeader = message.getMetadata(KafkaMessageMetadata.class)
                .get()
                .getHeaders().headers(X_RH_NOTIFICATIONS_CONNECTOR_HEADER)
                .iterator().next().value();
        assertEquals(getExpectedConnectorHeader(), new String(actualConnectorHeader, UTF_8));
    }

    protected void mockTemplate() {
        Template template = new Template();
        template.setName("Test template");
        template.setData(getQuteTemplate());

        IntegrationTemplate integrationTemplate = new IntegrationTemplate();
        integrationTemplate.setTheTemplate(template);

        when(templateRepository.findIntegrationTemplate(any(), any(), any(), any(), any())).thenReturn(Optional.of(integrationTemplate));
    }

    protected static Event buildEvent() {
        Action action = new Action.ActionBuilder()
                .withBundle("rhel")
                .withApplication("policies")
                .withEventType("policy-triggered")
                .withOrgId(DEFAULT_ORG_ID)
                .withTimestamp(LocalDateTime.now(UTC))
                .withContext(new Context.ContextBuilder()
                        .withAdditionalProperty("inventory_id", "6ad30f3e-0497-4e74-99f1-b3f9a6120a6f")
                        .withAdditionalProperty("display_name", "my-computer")
                        .build()
                )
                .withEvents(List.of(
                        new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                                .withMetadata(new Metadata.MetadataBuilder().build())
                                .withPayload(new Payload.PayloadBuilder()
                                        .withAdditionalProperty("foo", "bar")
                                        .build()
                                ).build()
                ))
                .build();

        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setOrgId(DEFAULT_ORG_ID);
        event.setEventWrapper(new EventWrapperAction(action));
        event.setApplicationDisplayName("policies");
        EventType eventType = new EventType();
        eventType.setName("policy-triggered");
        event.setEventType(eventType);

        return event;
    }

    protected Endpoint buildEndpoint() {
        CamelProperties properties = new CamelProperties();
        properties.setUrl(WEBHOOK_URL);
        addExtraEndpointProperties(properties);

        Endpoint endpoint = new Endpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setOrgId(DEFAULT_ORG_ID);
        endpoint.setType(CAMEL);
        endpoint.setSubType(getSubType());
        endpoint.setProperties(properties);

        return endpoint;
    }

    protected void addExtraEndpointProperties(CamelProperties properties) {
    }

    protected abstract String getExpectedConnectorHeader();

    protected String getExpectedCloudEventType() {
        return CLOUD_EVENT_TYPE_PREFIX + getExpectedConnectorHeader();
    }
}
