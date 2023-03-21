package com.redhat.cloud.notifications.processors.slack;

import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
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
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class SlackProcessorTest {

    private static final String WEBHOOK_URL = "https://foo.bar";
    private static final String CHANNEL = "#notifications";
    private static final String SLACK_TEMPLATE = "{#if data.context.display_name??}" +
            "<{data.environment_url}/insights/inventory/{data.context.inventory_id}|{data.context.display_name}> " +
            "triggered {data.events.size()} event{#if data.events.size() > 1}s{/if}" +
            "{#else}{data.events.size()} event{#if data.events.size() > 1}s{/if} triggered{/if} " +
            "from {data.bundle}/{data.application}. " +
            "<{data.environment_url}/insights/{data.application}|Open {data.application}>";
    private static final String SLACK_EXPECTED_MSG = "<//insights/inventory/6ad30f3e-0497-4e74-99f1-b3f9a6120a6f|my-computer> " +
            "triggered 1 event from rhel/policies. <//insights/policies|Open policies>";

    @Inject
    SlackProcessor slackProcessor;

    @InjectMock
    TemplateRepository templateRepository;

    @InjectMock
    NotificationHistoryRepository notificationHistoryRepository;

    @InjectMock
    @RestClient
    InternalTemporarySlackService internalTemporarySlackService;

    @Test
    void testProcess() {
        mockTemplate();
        Event event = buildEvent();
        Endpoint endpoint = buildEndpoint();

        slackProcessor.process(event, List.of(endpoint));

        verify(templateRepository, times(1)).findIntegrationTemplate(any(), any(), any(), any());
        verify(notificationHistoryRepository, times(1)).createNotificationHistory(any(NotificationHistory.class));
        ArgumentCaptor<SlackNotification> argumentCaptor = ArgumentCaptor.forClass(SlackNotification.class);
        verify(internalTemporarySlackService, times(1)).send(argumentCaptor.capture());
        assertEquals(DEFAULT_ORG_ID, argumentCaptor.getValue().orgId);
        assertNotNull(argumentCaptor.getValue().historyId);
        assertEquals(WEBHOOK_URL, argumentCaptor.getValue().webhookUrl);
        assertEquals(CHANNEL, argumentCaptor.getValue().channel);
        assertEquals(SLACK_EXPECTED_MSG, argumentCaptor.getValue().message);
    }

    private void mockTemplate() {
        Template template = new Template();
        template.setName("Test template");
        template.setData(SLACK_TEMPLATE);

        IntegrationTemplate integrationTemplate = new IntegrationTemplate();
        integrationTemplate.setTheTemplate(template);

        when(templateRepository.findIntegrationTemplate(any(), any(), any(), any())).thenReturn(Optional.of(integrationTemplate));
    }

    private static Event buildEvent() {
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
        event.setAction(action);

        return event;
    }

    private static Endpoint buildEndpoint() {
        CamelProperties properties = new CamelProperties();
        properties.setUrl(WEBHOOK_URL);
        properties.setExtras(Map.of("channel", CHANNEL));

        Endpoint endpoint = new Endpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setOrgId(DEFAULT_ORG_ID);
        endpoint.setType(CAMEL);
        endpoint.setSubType("slack");
        endpoint.setProperties(properties);

        return endpoint;
    }
}
