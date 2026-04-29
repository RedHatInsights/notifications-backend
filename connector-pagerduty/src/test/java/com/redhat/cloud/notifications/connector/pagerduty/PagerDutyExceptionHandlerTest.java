package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.pagerduty.config.PagerDutyConnectorConfig;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledExceptionDetails;
import io.quarkus.test.InjectMock;
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
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(PagerDutyTestLifecycleManager.class)
class PagerDutyExceptionHandlerTest {

    static final String PAGERDUTY_URL = "https://events.pagerduty.com/v2/enqueue";

    @Inject
    PagerDutyExceptionHandler exceptionHandler;

    @InjectMock
    PagerDutyConnectorConfig connectorConfig;

    @Test
    void testExtractsTargetUrlFromConfig() {
        when(connectorConfig.getPagerDutyUrl()).thenReturn(PAGERDUTY_URL);

        JsonObject data = new JsonObject()
            .put("org_id", DEFAULT_ORG_ID);

        IncomingCloudEventMetadata<JsonObject> cloudEvent = buildIncomingCloudEvent("test-id", "test-type", data);

        HandledExceptionDetails result = exceptionHandler.process(new RuntimeException("test"), cloudEvent);

        HandledHttpExceptionDetails httpDetails = assertInstanceOf(HandledHttpExceptionDetails.class, result);
        assertEquals(PAGERDUTY_URL, httpDetails.targetUrl);
    }
}
