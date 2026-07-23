package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.model.HandledEmailExceptionDetails;
import com.redhat.cloud.notifications.connector.email.payload.PayloadDetails;
import com.redhat.cloud.notifications.connector.v2.BaseConnectorIntegrationTest;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpExceptionDetails;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class EmailExceptionProcessorTest {

    @Inject
    EmailExceptionProcessor emailExceptionProcessor;

    @Test
    void testPayloadIdIsExtractedFromData() {
        JsonObject payload = new JsonObject()
            .put("org_id", "12345")
            .put("endpoint_properties", new JsonObject())
            .put("payload", new JsonObject())
            .put(PayloadDetails.PAYLOAD_DETAILS_ID_KEY, "my-payload-id");

        HandledHttpExceptionDetails result = emailExceptionProcessor.process(
            new RuntimeException("test"),
            BaseConnectorIntegrationTest.buildIncomingCloudEvent("test-id", "com.redhat.console.notification.toCamel.email", payload)
        );

        assertInstanceOf(HandledEmailExceptionDetails.class, result);
        assertEquals("my-payload-id", ((HandledEmailExceptionDetails) result).payloadId);
    }

    @Test
    void testPayloadIdIsNullWhenKeyAbsent() {
        JsonObject payload = new JsonObject()
            .put("org_id", "12345")
            .put("endpoint_properties", new JsonObject())
            .put("payload", new JsonObject());

        HandledHttpExceptionDetails result = emailExceptionProcessor.process(
            new RuntimeException("test"),
            BaseConnectorIntegrationTest.buildIncomingCloudEvent("test-id", "com.redhat.console.notification.toCamel.email", payload)
        );

        assertInstanceOf(HandledEmailExceptionDetails.class, result);
        assertNull(((HandledEmailExceptionDetails) result).payloadId);
    }

    @Test
    void testResultIsAlwaysHandledEmailExceptionDetails() {
        JsonObject payload = new JsonObject()
            .put("org_id", "12345")
            .put("endpoint_properties", new JsonObject())
            .put("payload", new JsonObject())
            .put(PayloadDetails.PAYLOAD_DETAILS_ID_KEY, "pid-123");

        HandledHttpExceptionDetails result = emailExceptionProcessor.process(
            new RuntimeException("test"),
            BaseConnectorIntegrationTest.buildIncomingCloudEvent("test-id", "com.redhat.console.notification.toCamel.email", payload)
        );

        assertInstanceOf(HandledEmailExceptionDetails.class, result);
        assertEquals("pid-123", ((HandledEmailExceptionDetails) result).payloadId);
    }
}
