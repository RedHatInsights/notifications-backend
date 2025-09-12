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
 * Unit tests for ErrorProcessor.
 */
@ExtendWith(MockitoExtension.class)
class ErrorProcessorTest {

    @Mock
    Message<JsonObject> message;

    @Test
    void testProcessErrorWithValidCloudEventData() {
        // Given
        ErrorProcessor processor = new ErrorProcessor();
        CloudEventData cloudEventData = new CloudEventData("test-org", "test-history", "test-connector", new JsonObject());
        RuntimeException error = new RuntimeException("Test error");
        // message is already mocked

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> processor.processError(cloudEventData, error, message));
    }

    @Test
    void testProcessErrorWithNullCloudEventData() {
        // Given
        ErrorProcessor processor = new ErrorProcessor();
        RuntimeException error = new RuntimeException("Test error");
        // message is already mocked

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> processor.processError(null, error, message));
    }

    @Test
    void testProcessErrorWithNullError() {
        // Given
        ErrorProcessor processor = new ErrorProcessor();
        CloudEventData cloudEventData = new CloudEventData("test-org", "test-history", "test-connector", new JsonObject());
        // message is already mocked

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> processor.processError(cloudEventData, null, message));
    }

    @Test
    void testProcessErrorWithNullMessage() {
        // Given
        ErrorProcessor processor = new ErrorProcessor();
        CloudEventData cloudEventData = new CloudEventData("test-org", "test-history", "test-connector", new JsonObject());
        RuntimeException error = new RuntimeException("Test error");

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> processor.processError(cloudEventData, error, null));
    }
}

