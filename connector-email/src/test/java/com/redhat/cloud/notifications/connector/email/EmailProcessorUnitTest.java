package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.clients.BOPClient;
import com.redhat.cloud.notifications.connector.email.dto.BOPRequest;
import com.redhat.cloud.notifications.connector.email.dto.BOPResponse;
import com.redhat.cloud.notifications.connector.email.dto.EmailNotification;
import com.redhat.cloud.notifications.connector.email.processors.EmailProcessor;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit test for EmailProcessor without Quarkus dependencies.
 */
@ExtendWith(MockitoExtension.class)
class EmailProcessorUnitTest {

    @Mock
    BOPClient bopClient;

    private EmailProcessor emailProcessor;

    @BeforeEach
    void setUp() {
        emailProcessor = new EmailProcessor();
        // Use reflection to inject the mock client
        try {
            java.lang.reflect.Field field = EmailProcessor.class.getDeclaredField("bopClient");
            field.setAccessible(true);
            field.set(emailProcessor, bopClient);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock BOPClient", e);
        }
    }

    @Test
    void testProcessEmailSuccess() {
        // Given
        EmailNotification emailNotification = new EmailNotification();
        emailNotification.setSubject("Test Subject");
        emailNotification.setBody("Test Body");
        emailNotification.setSender("test@example.com");
        emailNotification.setOrgId("default-org-id");
        emailNotification.setRecipients(Set.of("user1@example.com"));
        emailNotification.setEventType("test-event");

        BOPResponse mockResponse = new BOPResponse(true, "msg-123", null, 200);
        when(bopClient.sendEmail(any(BOPRequest.class))).thenReturn(mockResponse);

        // When
        JsonObject result = emailProcessor.processEmail(emailNotification);

        // Then
        assertNotNull(result);
        assertTrue(result.getBoolean("success"));
        assertEquals("msg-123", result.getString("messageId"));
        assertEquals(200, result.getInteger("statusCode"));
    }

    @Test
    void testProcessEmailFailure() {
        // Given
        EmailNotification emailNotification = new EmailNotification();
        emailNotification.setSubject("Test Subject");
        emailNotification.setBody("Test Body");
        emailNotification.setSender("test@example.com");
        emailNotification.setOrgId("default-org-id");
        emailNotification.setRecipients(Set.of("user1@example.com"));
        emailNotification.setEventType("test-event");

        BOPResponse mockResponse = new BOPResponse(false, null, "BOP service error", 500);
        when(bopClient.sendEmail(any(BOPRequest.class))).thenReturn(mockResponse);

        // When
        JsonObject result = emailProcessor.processEmail(emailNotification);

        // Then
        assertNotNull(result);
        assertFalse(result.getBoolean("success"));
        assertEquals("BOP service error", result.getString("error"));
        assertEquals(500, result.getInteger("statusCode"));
    }

    @Test
    void testProcessEmailException() {
        // Given
        EmailNotification emailNotification = new EmailNotification();
        emailNotification.setSubject("Test Subject");
        emailNotification.setBody("Test Body");
        emailNotification.setSender("test@example.com");
        emailNotification.setOrgId("default-org-id");
        emailNotification.setRecipients(Set.of("user1@example.com"));
        emailNotification.setEventType("test-event");

        when(bopClient.sendEmail(any(BOPRequest.class)))
            .thenThrow(new RuntimeException("Network error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> emailProcessor.processEmail(emailNotification));
    }
}
