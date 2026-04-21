package com.redhat.cloud.notifications.connector.splunk;

import com.redhat.cloud.notifications.connector.v2.TestLifecycleManager;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledExceptionDetails;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.connector.v2.BaseConnectorIntegrationTest.buildIncomingCloudEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class SplunkExceptionHandlerTest {

    @Inject
    SplunkExceptionHandler exceptionHandler;

    @Test
    void testExtractsTargetUrlFromMetadata() {
        JsonObject metadata = JsonObject.of("url", "https://splunk.example.com");
        JsonObject data = new JsonObject()
            .put("org_id", DEFAULT_ORG_ID)
            .put("notif-metadata", metadata);

        IncomingCloudEventMetadata<JsonObject> cloudEvent = buildIncomingCloudEvent("test-id", "test-type", data);

        HandledExceptionDetails result = exceptionHandler.process(new RuntimeException("test"), cloudEvent);

        HandledHttpExceptionDetails httpDetails = assertInstanceOf(HandledHttpExceptionDetails.class, result);
        assertEquals("https://splunk.example.com", httpDetails.targetUrl);
    }

    @Test
    void testHandlesMissingMetadata() {
        JsonObject data = new JsonObject()
            .put("org_id", DEFAULT_ORG_ID);

        IncomingCloudEventMetadata<JsonObject> cloudEvent = buildIncomingCloudEvent("test-id", "test-type", data);

        HandledExceptionDetails result = exceptionHandler.process(new RuntimeException("test"), cloudEvent);

        assertNotNull(result);
        HandledHttpExceptionDetails httpDetails = assertInstanceOf(HandledHttpExceptionDetails.class, result);
        assertNull(httpDetails.targetUrl);
    }

    @Test
    void testHandlesNullMetadata() {
        JsonObject data = new JsonObject()
            .put("org_id", DEFAULT_ORG_ID)
            .putNull("notif-metadata");

        IncomingCloudEventMetadata<JsonObject> cloudEvent = buildIncomingCloudEvent("test-id", "test-type", data);

        HandledExceptionDetails result = exceptionHandler.process(new RuntimeException("test"), cloudEvent);

        assertNotNull(result);
        HandledHttpExceptionDetails httpDetails = assertInstanceOf(HandledHttpExceptionDetails.class, result);
        assertNull(httpDetails.targetUrl);
    }

    @Test
    void testHandlesMetadataWithoutUrl() {
        JsonObject metadata = JsonObject.of("other-key", "value");
        JsonObject data = new JsonObject()
            .put("org_id", DEFAULT_ORG_ID)
            .put("notif-metadata", metadata);

        IncomingCloudEventMetadata<JsonObject> cloudEvent = buildIncomingCloudEvent("test-id", "test-type", data);

        HandledExceptionDetails result = exceptionHandler.process(new RuntimeException("test"), cloudEvent);

        assertNotNull(result);
        HandledHttpExceptionDetails httpDetails = assertInstanceOf(HandledHttpExceptionDetails.class, result);
        assertNull(httpDetails.targetUrl);
    }
}
