package com.redhat.cloud.notifications.transformers;

import com.redhat.cloud.notifications.AdvisorTestHelpers;
import com.redhat.cloud.notifications.ErrataTestHelpers;
import com.redhat.cloud.notifications.InventoryTestHelpers;
import com.redhat.cloud.notifications.OcmTestHelpers;
import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Event;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.*;
import static com.redhat.cloud.notifications.transformers.BaseTransformer.SEVERITY;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class SeverityTransformerTest {

    @Inject
    BaseTransformer baseTransformer;

    private final SeverityTransformer severityTransformer = new SeverityTransformer();

    @Test
    public void testPayloadWithSeverity() {
        Action action = TestHelpers.createIntegrationsFailedAction();
        action.setSeverity(Severity.MODERATE.name()); // "MODERATE"
        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(action));

        JsonObject data = baseTransformer.toJsonObject(event);
        assertEquals(Severity.MODERATE.name(), severityTransformer.getSeverity(data));

        // Check with mixed case severity string
        action.setSeverity("iMpOrTaNt");

        JsonObject dataMixed = baseTransformer.toJsonObject(event);
        assertEquals(Severity.IMPORTANT.name(), severityTransformer.getSeverity(dataMixed));
    }

    @Test
    public void testPayloadWithInvalidSeverity() {
        Action action = TestHelpers.createIntegrationsFailedAction();
        action.setSeverity("this is not a valid severity level");
        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(action));

        JsonObject data = baseTransformer.toJsonObject(event);
        assertEquals(Severity.UNDEFINED.name(), severityTransformer.getSeverity(data));
    }

    @Test
    public void testPayloadWithoutSeverity() {
        Action action = TestHelpers.createIntegrationsFailedAction();
        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(action));

        JsonObject data = baseTransformer.toJsonObject(event);
        assertEquals(Severity.UNDEFINED.name(), severityTransformer.getSeverity(data));
    }

    @Test
    public void testErrataPayloadWithLegacySeverity() {
        // Single event
        Action singleEventAction = ErrataTestHelpers.createErrataAction();
        com.redhat.cloud.notifications.ingress.Event firstEvent = singleEventAction.getEvents().getFirst();
        singleEventAction.setEvents(List.of(firstEvent));

        Event singleEvent = new Event();
        singleEvent.setEventWrapper(new EventWrapperAction(singleEventAction));

        JsonObject singleEventData = baseTransformer.toJsonObject(singleEvent);
        assertEquals(Severity.MODERATE.name(), severityTransformer.getSeverity(singleEventData));

        // Multiple events, selects the highest severity found
        Action multipleEventAction = ErrataTestHelpers.createErrataAction();
        Event multipleEvent = new Event();
        multipleEvent.setEventWrapper(new EventWrapperAction(multipleEventAction));

        JsonObject multipleEventData = baseTransformer.toJsonObject(multipleEvent);
        assertEquals(Severity.IMPORTANT.name(), severityTransformer.getSeverity(multipleEventData));
    }

    @Test
    public void testOcmPayloadWithLegacySeverity() {
        Optional<Map<String, Object>> legacySeverity = Optional.of(Map.of(SEVERITY, SeverityTransformer.OcmServiceLogSeverity.INFO.name()));
        Action action = OcmTestHelpers.createOcmAction("test-cluster-name", "Premium", "System rebooting",
                "System reboot in progress", "test-title", legacySeverity);
        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(action));

        JsonObject data = baseTransformer.toJsonObject(event);
        assertEquals(Severity.LOW.name(), severityTransformer.getSeverity(data));
    }

    @Test
    public void testInventoryPayloadWithLegacySeverity() {
        Action action = InventoryTestHelpers.createInventoryAction("test-tenant", "rhel", "inventory", "validation-error");
        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(action));

        JsonObject data = baseTransformer.toJsonObject(event);
        assertEquals(Severity.IMPORTANT.name(), severityTransformer.getSeverity(data));
    }

    @Test
    public void testAdvisorPayloadWithLegacySeverity() {
        // Single event, risk as string
        Action singleAction = AdvisorTestHelpers.createAction("test-event-type", Map.of(
                RULE_ID, UUID.randomUUID().toString(),
                RULE_DESCRIPTION, "this is a rule",
                TOTAL_RISK, "4", // Critical
                HAS_INCIDENT, "false",
                RULE_URL, UUID.randomUUID().toString()
        ));
        Event singleEvent = new Event();
        singleEvent.setEventWrapper(new EventWrapperAction(singleAction));

        JsonObject singleData = baseTransformer.toJsonObject(singleEvent);
        assertEquals(Severity.CRITICAL.name(), severityTransformer.getSeverity(singleData));

        // Multiple events, risk as integer
        Action multipleAction = TestHelpers.createAdvisorAction("test-account-id", "deactivated-recommendation");
        Event multipleEvent = new Event();
        multipleEvent.setEventWrapper(new EventWrapperAction(multipleAction));

        JsonObject multipleData = baseTransformer.toJsonObject(multipleEvent);
        assertEquals(Severity.MODERATE.name(), severityTransformer.getSeverity(multipleData));
    }

    /** This is not an expected case, but it should still be handled gracefully. */
    @Test
    public void testOcmPayloadWithInvalidLegacySeverity() {
        Optional<Map<String, Object>> legacySeverity = Optional.of(Map.of(SEVERITY, "this is not a valid severity level"));
        Action action = OcmTestHelpers.createOcmAction("test-cluster-name", "Premium", "System rebooting",
                "System reboot in progress", "test-title", legacySeverity);
        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(action));

        JsonObject data = baseTransformer.toJsonObject(event);
        assertEquals(Severity.UNDEFINED.name(), severityTransformer.getSeverity(data));
    }
}
