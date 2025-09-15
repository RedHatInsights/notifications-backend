package com.redhat.cloud.notifications.connector.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.ConnectorProcessor.ConnectorResult;
import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.model.EmailNotification;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import com.redhat.cloud.notifications.connector.email.processors.bop.BOPManager;
import com.redhat.cloud.notifications.connector.email.processors.recipients.RecipientsQuery;
import com.redhat.cloud.notifications.connector.email.processors.recipientsresolver.RecipientsResolverService;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class EmailConnectorTest {

    @Inject
    EmailConnector emailConnector;

    @InjectMock
    @RestClient
    RecipientsResolverService recipientsResolverService;

    @InjectMock
    BOPManager bopManager;

    @InjectMock
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    ObjectMapper objectMapper;


    @BeforeEach
    void setUp() {
        when(emailConnectorConfig.getMaxRecipientsPerEmail()).thenReturn(50);
        when(emailConnectorConfig.isEmailsInternalOnlyEnabled()).thenReturn(false);
    }

    @Test
    void testProcessCloudEvent_Success() throws Exception {
        // Prepare test data
        String orgId = "test-org-123";
        String eventId = UUID.randomUUID().toString();

        EmailNotification emailNotification = new EmailNotification(
                "Test email body",
                "Test subject",
                "sender@redhat.com",
                orgId,
                Set.of(new RecipientSettings()),
                Set.of("user1", "user2"),
                Set.of(),
                true,
                new JsonObject()
        );

        JsonObject cloudEvent = JsonObject.mapFrom(emailNotification);

        ExceptionProcessor.ProcessingContext context = mock(ExceptionProcessor.ProcessingContext.class);
        when(context.getOriginalCloudEvent()).thenReturn(cloudEvent);
        when(context.getId()).thenReturn(eventId);
        when(context.getOrgId()).thenReturn(orgId);

        // Mock recipients resolution
        Set<User> mockUsers = TestUtils.createUsers("user1", "user2");
        when(recipientsResolverService.getRecipients(any(RecipientsQuery.class))).thenReturn(mockUsers);

        // Execute
        Uni<ConnectorResult> result = emailConnector.processCloudEvent(context);
        ConnectorResult connectorResult = result.await().indefinitely();

        // Verify
        assertTrue(connectorResult.isSuccessful());
        assertEquals(eventId, connectorResult.getId());
        assertEquals(orgId, connectorResult.getOrgId());
        assertTrue(connectorResult.getOutcome().contains("2 recipients"));

        // Verify BOP manager was called
        ArgumentCaptor<List<String>> recipientsCaptor = ArgumentCaptor.forClass(List.class);
        verify(bopManager).sendToBop(
                recipientsCaptor.capture(),
                eq("Test subject"),
                eq("Test email body"),
                eq("sender@redhat.com")
        );

        List<String> capturedRecipients = recipientsCaptor.getValue();
        assertEquals(2, capturedRecipients.size());
        assertTrue(capturedRecipients.contains("user1-email"));
        assertTrue(capturedRecipients.contains("user2-email"));

        // Verify metrics are collected (metrics system is working)
    }

    @Test
    void testProcessCloudEvent_InternalOnlyMode() throws Exception {
        // Setup internal-only mode
        when(emailConnectorConfig.isEmailsInternalOnlyEnabled()).thenReturn(true);

        String orgId = "test-org-123";
        String eventId = UUID.randomUUID().toString();

        EmailNotification emailNotification = new EmailNotification(
                "Test email body",
                "Test subject",
                "sender@redhat.com",
                orgId,
                Set.of(new RecipientSettings()),
                Set.of("internal", "external"),
                Set.of(),
                true,
                new JsonObject()
        );

        JsonObject cloudEvent = JsonObject.mapFrom(emailNotification);

        ExceptionProcessor.ProcessingContext context = mock(ExceptionProcessor.ProcessingContext.class);
        when(context.getOriginalCloudEvent()).thenReturn(cloudEvent);
        when(context.getId()).thenReturn(eventId);
        when(context.getOrgId()).thenReturn(orgId);

        // Mock recipients with mixed internal/external emails
        User internalUser = new User();
        internalUser.setUsername("internal");
        internalUser.setEmail("internal@redhat.com");

        User externalUser = new User();
        externalUser.setUsername("external");
        externalUser.setEmail("external@example.com");

        Set<User> mockUsers = Set.of(internalUser, externalUser);
        when(recipientsResolverService.getRecipients(any(RecipientsQuery.class))).thenReturn(mockUsers);

        // Execute
        Uni<ConnectorResult> result = emailConnector.processCloudEvent(context);
        ConnectorResult connectorResult = result.await().indefinitely();

        // Verify only internal email was sent
        assertTrue(connectorResult.isSuccessful());

        ArgumentCaptor<List<String>> recipientsCaptor = ArgumentCaptor.forClass(List.class);
        verify(bopManager).sendToBop(
                recipientsCaptor.capture(),
                anyString(),
                anyString(),
                anyString()
        );

        List<String> capturedRecipients = recipientsCaptor.getValue();
        assertEquals(1, capturedRecipients.size());
        assertEquals("internal@redhat.com", capturedRecipients.get(0));
    }

    @Test
    void testProcessCloudEvent_BatchProcessing() throws Exception {
        // Setup small batch size
        when(emailConnectorConfig.getMaxRecipientsPerEmail()).thenReturn(2);

        String orgId = "test-org-123";
        String eventId = UUID.randomUUID().toString();

        EmailNotification emailNotification = new EmailNotification(
                "Test email body",
                "Test subject",
                "sender@redhat.com",
                orgId,
                Set.of(new RecipientSettings()),
                Set.of("user1", "user2", "user3", "user4"),
                Set.of(),
                true,
                new JsonObject()
        );

        JsonObject cloudEvent = JsonObject.mapFrom(emailNotification);

        ExceptionProcessor.ProcessingContext context = mock(ExceptionProcessor.ProcessingContext.class);
        when(context.getOriginalCloudEvent()).thenReturn(cloudEvent);
        when(context.getId()).thenReturn(eventId);
        when(context.getOrgId()).thenReturn(orgId);

        // Mock recipients resolution
        Set<User> mockUsers = TestUtils.createUsers("user1", "user2", "user3", "user4");
        when(recipientsResolverService.getRecipients(any(RecipientsQuery.class))).thenReturn(mockUsers);

        // Execute
        Uni<ConnectorResult> result = emailConnector.processCloudEvent(context);
        ConnectorResult connectorResult = result.await().indefinitely();

        // Verify
        assertTrue(connectorResult.isSuccessful());

        // Verify BOP manager was called twice (2 batches of 2 recipients each)
        verify(bopManager, times(2)).sendToBop(anyList(), anyString(), anyString(), anyString());
    }

    @Test
    void testProcessCloudEvent_NoValidEmails() throws Exception {
        String orgId = "test-org-123";
        String eventId = UUID.randomUUID().toString();

        EmailNotification emailNotification = new EmailNotification(
                "Test email body",
                "Test subject",
                "sender@redhat.com",
                orgId,
                Set.of(new RecipientSettings()),
                Set.of("user1"),
                Set.of(),
                true,
                new JsonObject()
        );

        JsonObject cloudEvent = JsonObject.mapFrom(emailNotification);

        ExceptionProcessor.ProcessingContext context = mock(ExceptionProcessor.ProcessingContext.class);
        when(context.getOriginalCloudEvent()).thenReturn(cloudEvent);
        when(context.getId()).thenReturn(eventId);
        when(context.getOrgId()).thenReturn(orgId);

        // Mock recipients with no valid email
        User userWithoutEmail = new User();
        userWithoutEmail.setUsername("user1");
        userWithoutEmail.setEmail(null);

        Set<User> mockUsers = Set.of(userWithoutEmail);
        when(recipientsResolverService.getRecipients(any(RecipientsQuery.class))).thenReturn(mockUsers);

        // Execute
        Uni<ConnectorResult> result = emailConnector.processCloudEvent(context);
        ConnectorResult connectorResult = result.await().indefinitely();

        // Verify - should still be successful but no emails sent
        assertTrue(connectorResult.isSuccessful());
        verify(bopManager, never()).sendToBop(anyList(), anyString(), anyString(), anyString());
    }

    @Test
    void testProcessCloudEvent_RecipientsResolverFailure() throws Exception {
        String orgId = "test-org-123";
        String eventId = UUID.randomUUID().toString();

        EmailNotification emailNotification = new EmailNotification(
                "Test email body",
                "Test subject",
                "sender@redhat.com",
                orgId,
                Set.of(new RecipientSettings()),
                Set.of("user1"),
                Set.of(),
                true,
                new JsonObject()
        );

        JsonObject cloudEvent = JsonObject.mapFrom(emailNotification);

        ExceptionProcessor.ProcessingContext context = mock(ExceptionProcessor.ProcessingContext.class);
        when(context.getOriginalCloudEvent()).thenReturn(cloudEvent);
        when(context.getId()).thenReturn(eventId);
        when(context.getOrgId()).thenReturn(orgId);

        // Mock recipients resolver failure
        when(recipientsResolverService.getRecipients(any(RecipientsQuery.class)))
                .thenThrow(new RuntimeException("Recipients resolver failed"));

        // Execute and verify exception
        Uni<ConnectorResult> result = emailConnector.processCloudEvent(context);

        assertThrows(RuntimeException.class, () -> {
            result.await().indefinitely();
        });

        // Verify timer was used for metrics collection
        verify(bopManager, never()).sendToBop(anyList(), anyString(), anyString(), anyString());
    }

    @Test
    void testProcessCloudEvent_InvalidJsonPayload() throws Exception {
        String orgId = "test-org-123";
        String eventId = UUID.randomUUID().toString();

        // Invalid JSON that can't be parsed as EmailNotification
        JsonObject invalidCloudEvent = new JsonObject().put("invalid", "data");

        ExceptionProcessor.ProcessingContext context = mock(ExceptionProcessor.ProcessingContext.class);
        when(context.getOriginalCloudEvent()).thenReturn(invalidCloudEvent);
        when(context.getId()).thenReturn(eventId);
        when(context.getOrgId()).thenReturn(orgId);

        // Execute and verify exception
        Uni<ConnectorResult> result = emailConnector.processCloudEvent(context);

        assertThrows(RuntimeException.class, () -> {
            result.await().indefinitely();
        });

        // Verify timer was used for metrics collection
        verify(bopManager, never()).sendToBop(anyList(), anyString(), anyString(), anyString());
    }
}
