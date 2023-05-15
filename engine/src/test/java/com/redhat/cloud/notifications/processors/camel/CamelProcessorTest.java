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
import com.redhat.cloud.notifications.models.IntegrationTemplate;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.Template;
import io.quarkus.test.junit.mockito.InjectMock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
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

    protected abstract String getCuteTemplate();

    protected abstract String getExpectedMessage();

    protected abstract String getSubType();

    protected abstract CamelProcessor getCamelProcessor();


    @Test
    void testProcess() {
        mockTemplate();
        Event event = buildEvent();
        Endpoint endpoint = buildEndpoint();
        getCamelProcessor().process(event, List.of(endpoint));

        verify(templateRepository, times(1)).findIntegrationTemplate(any(), any(), any(), any());
        verify(notificationHistoryRepository, times(1)).createNotificationHistory(any(NotificationHistory.class));
        argumentCaptorChecks();
    }

    protected void argumentCaptorChecks() {
        ArgumentCaptor<CamelNotification> argumentCaptor = ArgumentCaptor.forClass(CamelNotification.class);
        verify(getInternalClient(), times(1)).send(argumentCaptor.capture());
        assertEquals(DEFAULT_ORG_ID, argumentCaptor.getValue().orgId);
        assertNotNull(argumentCaptor.getValue().historyId);
        assertEquals(WEBHOOK_URL, argumentCaptor.getValue().webhookUrl);
        assertEquals(getExpectedMessage(), argumentCaptor.getValue().message);
    }

    protected InternalCamelTemporaryService getInternalClient() {
        fail("Client must be checked");
        return null;
    }

    protected void mockTemplate() {
        Template template = new Template();
        template.setName("Test template");
        template.setData(getCuteTemplate());

        IntegrationTemplate integrationTemplate = new IntegrationTemplate();
        integrationTemplate.setTheTemplate(template);

        when(templateRepository.findIntegrationTemplate(any(), any(), any(), any())).thenReturn(Optional.of(integrationTemplate));
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
        event.setEventWrapper(new EventWrapperAction(action));

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
}
