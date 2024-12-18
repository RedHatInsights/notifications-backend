package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTestUtils.buildExpectedOutgoingPayload;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTestUtils.createIncomingPayload;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.APPLICATION;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.CONTEXT;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.DISPLAY_NAME;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.ENVIRONMENT_URL;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.EVENTS;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.EVENT_TYPE;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.INVENTORY_URL;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.PAYLOAD;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.SEVERITY;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.SOURCE;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.TIMESTAMP;
import static org.apache.camel.test.junit5.TestSupport.createExchangeWithBody;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * These test cases are intended to verify that the {@link PagerDutyTransformer} can handle various possible inputs.
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class PagerDutyTransformerTest extends CamelQuarkusTestSupport {
    static final String TEST_URL = "https://example.com/";

    @Inject
    PagerDutyCloudEventDataExtractor extractor;

    @Inject
    PagerDutyTransformer transformer;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testMissingEventType() {
        JsonObject cloudEventData = createIncomingPayload(TEST_URL);
        JsonObject cloudPayload = cloudEventData.getJsonObject(PAYLOAD);
        cloudPayload.remove(EVENT_TYPE);
        cloudEventData.put(PAYLOAD, cloudPayload);

        verifyTransformExceptionThrown(cloudEventData, IllegalArgumentException.class, "Event type must be specified for PagerDuty payload summary");
    }

    @Test
    void testMissingSource() {
        JsonObject cloudEventData = createIncomingPayload(TEST_URL);
        JsonObject cloudPayload = cloudEventData.getJsonObject(PAYLOAD);
        cloudPayload.remove(APPLICATION);
        cloudEventData.put(PAYLOAD, cloudPayload);

        verifyTransformExceptionThrown(cloudEventData, IllegalArgumentException.class, "Application must be specified for PagerDuty payload source");
    }

    @Test
    void testMissingSeverity() {
        JsonObject cloudEventData = createIncomingPayload(TEST_URL);
        JsonObject cloudPayload = cloudEventData.getJsonObject(PAYLOAD);
        cloudPayload.remove(SEVERITY);
        cloudEventData.put(PAYLOAD, cloudPayload);

        verifyTransformExceptionThrown(cloudEventData, IllegalArgumentException.class, "Severity must be specified for PagerDuty payload");
    }

    @Test
    void testSuccessfulPayloadTransform() {
        JsonObject cloudEventData = createIncomingPayload(TEST_URL);

        JsonObject expectedPayload = buildExpectedOutgoingPayload(cloudEventData);
        validatePayloadTransform(cloudEventData, expectedPayload);
    }

    /**
     * This is a slightly modified version of a real test message generated from the {@code /endpoints/{uuid}/test}
     * integrations endpoint, with the account and org id replaced.
     */
    @Test
    void testSuccessfulTestMessage() {
        JsonObject cloudEventData = createIncomingPayload(TEST_URL);

        JsonObject cloudEventPayload = JsonObject.of(
                "version", "2.0.0",
                "id", "13bb45fe-28a9-416d-81a1-aeb9abb24d41",
                "bundle", "console",
                "application", "integrations",
                "event_type", "integration-test",
                "timestamp", "2024-10-10T14:20:22.485454606",
                "account_id", DEFAULT_ACCOUNT_ID,
                "org_id", DEFAULT_ORG_ID,
                "context", JsonObject.of("integration-uuid", "e531af59-1c7f-4a6a-89e3-2a68954db8e9"),
                "events", JsonArray.of(
                        JsonObject.of(
                                "metadata", JsonObject.of("meta-information", "Meta information about the action"),
                                "payload", JsonObject.of("message", "A test alert, should be critical severity")
                        )
                )
        );
        cloudEventPayload.put("recipients", JsonArray.of());
        cloudEventPayload.put("environment_url", "https://console.redhat.com");
        InsightsUrlsBuilder.buildInventoryUrl(cloudEventPayload)
                .ifPresent(url -> cloudEventPayload.put("inventory_url", url));
        InsightsUrlsBuilder.buildApplicationUrl(cloudEventPayload)
                .ifPresent(url -> cloudEventPayload.put("application_url", url));
        cloudEventPayload.put("severity", "warning");
        cloudEventData.put(PAYLOAD, cloudEventPayload);

        JsonObject expectedPayload = buildExpectedOutgoingPayload(cloudEventData);
        validatePayloadTransform(cloudEventData, expectedPayload);
    }

    @Test
    void testInvalidTimestampDropped() {
        JsonObject cloudEventData = createIncomingPayload(TEST_URL);
        JsonObject cloudPayload = cloudEventData.getJsonObject(PAYLOAD);
        cloudPayload.put(TIMESTAMP, "not a timestamp");
        cloudEventData.put(PAYLOAD, cloudPayload);

        JsonObject expectedPayload = buildExpectedOutgoingPayload(cloudEventData);
        validatePayloadTransform(cloudEventData, expectedPayload);
    }

    @Test
    void testMissingSourceNames() {
        JsonObject cloudEventData = createIncomingPayload(TEST_URL);
        JsonObject cloudPayload = cloudEventData.getJsonObject(PAYLOAD);
        cloudPayload.remove(SOURCE);

        JsonObject expectedPayload = buildExpectedOutgoingPayload(cloudEventData);
        validatePayloadTransform(cloudEventData, expectedPayload);
    }

    @Test
    void testMissingApplicationDisplayName() {
        JsonObject cloudEventData = createIncomingPayload(TEST_URL);
        JsonObject cloudPayload = cloudEventData.getJsonObject(PAYLOAD);
        JsonObject sourceNames = cloudPayload.getJsonObject(SOURCE);
        sourceNames.remove(APPLICATION);

        JsonObject expectedPayload = buildExpectedOutgoingPayload(cloudEventData);
        validatePayloadTransform(cloudEventData, expectedPayload);
    }

    @Test
    void testSourceWithoutDisplayNames() {
        JsonObject cloudEventData = createIncomingPayload(TEST_URL);
        JsonObject cloudPayload = cloudEventData.getJsonObject(PAYLOAD);
        cloudPayload.put(SOURCE, new JsonObject());
        cloudEventData.put(PAYLOAD, cloudPayload);

        JsonObject expectedPayload = buildExpectedOutgoingPayload(cloudEventData);
        validatePayloadTransform(cloudEventData, expectedPayload);
    }

    @Test
    void testMissingEvents() {
        JsonObject cloudEventData = createIncomingPayload(TEST_URL);
        JsonObject cloudPayload = cloudEventData.getJsonObject(PAYLOAD);
        cloudPayload.remove(EVENTS);

        JsonObject expectedPayload = buildExpectedOutgoingPayload(cloudEventData);
        validatePayloadTransform(cloudEventData, expectedPayload);
    }

    @Test
    void testWithClientDisplayName() {
        JsonObject cloudEventData = createIncomingPayload(TEST_URL);
        JsonObject cloudPayload = cloudEventData.getJsonObject(PAYLOAD);
        cloudPayload.put(CONTEXT, JsonObject.of(
                DISPLAY_NAME, "console",
                "inventory_id", "8a4a4f75-5319-4255-9eb5-1ee5a92efd7f"
        ));
        cloudEventData.put(PAYLOAD, cloudPayload);

        JsonObject expectedPayload = buildExpectedOutgoingPayload(cloudEventData);
        validatePayloadTransform(cloudEventData, expectedPayload);
    }

    @Test
    void testWithMissingClientDisplayName() {
        JsonObject cloudEventData = createIncomingPayload(TEST_URL);
        JsonObject cloudPayload = cloudEventData.getJsonObject(PAYLOAD);
        JsonObject context = cloudPayload.getJsonObject(CONTEXT);
        context.remove(DISPLAY_NAME);

        JsonObject expectedPayload = buildExpectedOutgoingPayload(cloudEventData);
        validatePayloadTransform(cloudEventData, expectedPayload);
    }

    @Test
    void testWithClientDisplayNameAndInventoryId() {
        JsonObject cloudEventData = createIncomingPayload(TEST_URL);
        JsonObject cloudPayload = cloudEventData.getJsonObject(PAYLOAD);
        cloudPayload.put(CONTEXT, JsonObject.of(
                DISPLAY_NAME, "console",
                "inventory_id", "8a4a4f75-5319-4255-9eb5-1ee5a92efd7f"
        ));
        cloudEventData.put(PAYLOAD, cloudPayload);
    }

    @Test
    void testWithMissingInventoryId() {
        JsonObject cloudEventData = createIncomingPayload(TEST_URL);
        JsonObject cloudPayload = cloudEventData.getJsonObject(PAYLOAD);
        JsonObject context = cloudPayload.getJsonObject(CONTEXT);
        context.remove("inventory_id");

        JsonObject expectedPayload = buildExpectedOutgoingPayload(cloudEventData);
        validatePayloadTransform(cloudEventData, expectedPayload);
    }

    @Test
    void testMissingEnvironmentUrl() {
        JsonObject cloudEventData = createIncomingPayload(TEST_URL);
        JsonObject cloudPayload = cloudEventData.getJsonObject(PAYLOAD);
        cloudPayload.remove(ENVIRONMENT_URL);

        JsonObject expectedPayload = buildExpectedOutgoingPayload(cloudEventData);
        validatePayloadTransform(cloudEventData, expectedPayload);
    }

    @Test
    void testMissingInventoryUrl() {
        JsonObject cloudEventData = createIncomingPayload(TEST_URL);
        JsonObject cloudPayload = cloudEventData.getJsonObject(PAYLOAD);
        cloudPayload.remove(INVENTORY_URL);

        JsonObject expectedPayload = buildExpectedOutgoingPayload(cloudEventData);
        validatePayloadTransform(cloudEventData, expectedPayload);
    }

    void verifyTransformExceptionThrown(JsonObject cloudEventData, Class<? extends Throwable> exceptionType, String exceptionMessage) {
        Exchange exchange = createExchangeWithBody(context, "I am not used!");

        extractor.extract(exchange, cloudEventData);
        assertThrows(exceptionType, () -> transformer.process(exchange), exceptionMessage);
    }

    /**
     * @param cloudEventData the cloud event, as provided to the connector
     * @param expectedPayload the PagerDuty payload expected to be sent
     */
    void validatePayloadTransform(JsonObject cloudEventData, JsonObject expectedPayload) {
        Exchange exchange = createExchangeWithBody(context, "I am not used!");

        extractor.extract(exchange, cloudEventData);
        transformer.process(exchange);

        /* This test does not check whether authentication fields are included,
         * as only the PagerDuty payload is validated
         */
        assertEquals(expectedPayload.encode(), exchange.getIn().getBody(String.class));
    }
}
