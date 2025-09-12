package com.redhat.cloud.notifications.connector.processors;

import com.redhat.cloud.notifications.connector.QuarkusConnectorBase.CloudEventData;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RedeliveryProcessor.
 */
@ExtendWith(MockitoExtension.class)
class RedeliveryProcessorTest {

    @Mock
    Message<JsonObject> message;

    @Test
    void testShouldRetryWithRetryableError() {
        // Given
        RedeliveryProcessor processor = new RedeliveryProcessor();
        CloudEventData cloudEventData = new CloudEventData("test-org", "test-history", "test-connector", new JsonObject());
        RuntimeException error = new RuntimeException("Connection timeout");

        // When
        boolean shouldRetry = processor.shouldRetry(cloudEventData, error, 3);

        // Then
        assertTrue(shouldRetry);
    }

    @Test
    void testShouldRetryWithNonRetryableError() {
        // Given
        RedeliveryProcessor processor = new RedeliveryProcessor();
        CloudEventData cloudEventData = new CloudEventData("test-org", "test-history", "test-connector", new JsonObject());
        RuntimeException error = new RuntimeException("Invalid request");

        // When
        boolean shouldRetry = processor.shouldRetry(cloudEventData, error, 3);

        // Then
        assertFalse(shouldRetry);
    }

    @Test
    void testShouldRetryWithNullCloudEventData() {
        // Given
        RedeliveryProcessor processor = new RedeliveryProcessor();
        RuntimeException error = new RuntimeException("Connection timeout");

        // When
        boolean shouldRetry = processor.shouldRetry(null, error, 3);

        // Then
        assertFalse(shouldRetry);
    }

    @Test
    void testShouldRetryWithZeroMaxAttempts() {
        // Given
        RedeliveryProcessor processor = new RedeliveryProcessor();
        CloudEventData cloudEventData = new CloudEventData("test-org", "test-history", "test-connector", new JsonObject());
        RuntimeException error = new RuntimeException("Connection timeout");

        // When
        boolean shouldRetry = processor.shouldRetry(cloudEventData, error, 0);

        // Then
        assertFalse(shouldRetry);
    }

    @Test
    void testScheduleRetryWithValidData() {
        // Given
        RedeliveryProcessor processor = new RedeliveryProcessor();
        CloudEventData cloudEventData = new CloudEventData("test-org", "test-history", "test-connector", new JsonObject());
        // message is already mocked

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> processor.scheduleRetry(cloudEventData, message, 1000L));
    }

    @Test
    void testScheduleRetryWithNullCloudEventData() {
        // Given
        RedeliveryProcessor processor = new RedeliveryProcessor();
        // message is already mocked

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> processor.scheduleRetry(null, message, 1000L));
    }

    @Test
    void testScheduleRetryWithNullMessage() {
        // Given
        RedeliveryProcessor processor = new RedeliveryProcessor();
        CloudEventData cloudEventData = new CloudEventData("test-org", "test-history", "test-connector", new JsonObject());

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> processor.scheduleRetry(cloudEventData, null, 1000L));
    }

    @Test
    void testRetryableErrorPatterns() {
        // Given
        RedeliveryProcessor processor = new RedeliveryProcessor();
        CloudEventData cloudEventData = new CloudEventData("test-org", "test-history", "test-connector", new JsonObject());

        // Test various retryable error patterns
        String[] retryableErrors = {"Connection timeout",
            "Network error",
            "Temporary failure",
            "Service unavailable",
            "HTTP 503",
            "HTTP 502",
            "HTTP 504"
        };

        for (String errorMessage : retryableErrors) {
            RuntimeException error = new RuntimeException(errorMessage);
            assertTrue(processor.shouldRetry(cloudEventData, error, 3),
                "Error '" + errorMessage + "' should be retryable");
        }
    }

    @Test
    void testNonRetryableErrorPatterns() {
        // Given
        RedeliveryProcessor processor = new RedeliveryProcessor();
        CloudEventData cloudEventData = new CloudEventData("test-org", "test-history", "test-connector", new JsonObject());

        // Test various non-retryable error patterns
        String[] nonRetryableErrors = {"Invalid request",
            "Authentication failed",
            "Permission denied",
            "HTTP 400",
            "HTTP 401",
            "HTTP 403",
            "HTTP 404"
        };

        for (String errorMessage : nonRetryableErrors) {
            RuntimeException error = new RuntimeException(errorMessage);
            assertFalse(processor.shouldRetry(cloudEventData, error, 3),
                "Error '" + errorMessage + "' should not be retryable");
        }
    }
}

