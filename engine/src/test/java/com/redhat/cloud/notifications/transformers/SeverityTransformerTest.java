package com.redhat.cloud.notifications.transformers;

import com.redhat.cloud.notifications.AdvisorTestHelpers;
import com.redhat.cloud.notifications.ErrataTestHelpers;
import com.redhat.cloud.notifications.InventoryTestHelpers;
import com.redhat.cloud.notifications.OcmTestHelpers;
import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.*;
import static com.redhat.cloud.notifications.transformers.BaseTransformer.SEVERITY;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class SeverityTransformerTest {

    @Inject
    SeverityTransformer severityTransformer;

    private static final EventType DEFAULT_EVENT_TYPE = new EventType();

    @BeforeAll
    static void init() {
        DEFAULT_EVENT_TYPE.setDefaultSeverity(Severity.UNDEFINED);
        DEFAULT_EVENT_TYPE.setAvailableSeverities(Set.of(Severity.values()));
    }

    @Test
    public void testPayloadWithSeverity() {
        Action action = TestHelpers.createIntegrationsFailedAction();
        action.setSeverity(Severity.MODERATE.name()); // "MODERATE"
        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(action));
        event.setEventType(DEFAULT_EVENT_TYPE);

        assertEquals(Severity.MODERATE, severityTransformer.getSeverity(event));

        // Check mixed case severity string, and transform payload
        action.setSeverity("iMpOrTaNt");
        Event eventMixed = new Event();
        eventMixed.setEventWrapper(new EventWrapperAction(action));
        eventMixed.setEventType(DEFAULT_EVENT_TYPE);

        assertEquals(Severity.IMPORTANT, severityTransformer.getSeverity(eventMixed));
    }

    @Test
    public void testPayloadWithInvalidSeverity() {
        Action action = TestHelpers.createIntegrationsFailedAction();
        action.setSeverity("this is not a valid severity level");
        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(action));
        event.setEventType(DEFAULT_EVENT_TYPE);

        assertEquals(Severity.UNDEFINED, severityTransformer.getSeverity(event));
    }

    @Test
    public void testPayloadWithSeverityNotMatchingWithAvailableSeverities() {
        final Application application = new Application();
        application.setName("test-severity-application");
        final EventType testEventType = new EventType();
        testEventType.setDefaultSeverity(Severity.MODERATE);
        testEventType.setAvailableSeverities(Set.of(Severity.MODERATE, Severity.IMPORTANT));
        testEventType.setApplication(application);

        Action action = TestHelpers.createIntegrationsFailedAction();
        action.setSeverity(Severity.CRITICAL.name());
        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(action));
        event.setEventType(testEventType);

        assertEquals(Severity.MODERATE, severityTransformer.getSeverity(event));
    }

    @Test
    public void testPayloadWithoutSeverity() {
        Action action = TestHelpers.createIntegrationsFailedAction();
        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(action));
        event.setEventType(DEFAULT_EVENT_TYPE);

        assertEquals(Severity.UNDEFINED, severityTransformer.getSeverity(event));
    }

    @Test
    public void testErrataPayloadWithLegacySeverity() {
        // Single event
        Action singleEventAction = ErrataTestHelpers.createErrataAction();
        com.redhat.cloud.notifications.ingress.Event firstEvent = singleEventAction.getEvents().getFirst();
        singleEventAction.setEvents(List.of(firstEvent));

        Event singleEvent = new Event();
        singleEvent.setEventWrapper(new EventWrapperAction(singleEventAction));
        singleEvent.setEventType(DEFAULT_EVENT_TYPE);

        assertEquals(Severity.MODERATE, severityTransformer.getSeverity(singleEvent));

        // Multiple events, selects the highest severity found
        Action multipleEventAction = ErrataTestHelpers.createErrataAction();
        Event multipleEvent = new Event();
        multipleEvent.setEventWrapper(new EventWrapperAction(multipleEventAction));
        multipleEvent.setEventType(DEFAULT_EVENT_TYPE);

        assertEquals(Severity.IMPORTANT, severityTransformer.getSeverity(multipleEvent));
    }

    @Test
    public void testOcmPayloadWithLegacySeverity() {
        Optional<Map<String, Object>> legacySeverity = Optional.of(Map.of(SEVERITY, SeverityTransformer.OcmServiceLogSeverity.INFO.name()));
        Action action = OcmTestHelpers.createOcmAction("test-cluster-name", "Premium", "System rebooting",
                "System reboot in progress", "test-title", legacySeverity);
        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(action));
        event.setEventType(DEFAULT_EVENT_TYPE);

        assertEquals(Severity.LOW, severityTransformer.getSeverity(event));
    }

    @Test
    public void testInventoryPayloadWithLegacySeverity() {
        Action action = InventoryTestHelpers.createInventoryAction("test-tenant", "rhel", "inventory", "validation-error");
        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(action));
        EventType eventType = new EventType();
        eventType.setAvailableSeverities(Set.of(Severity.values()));
        eventType.setDefaultSeverity(Severity.IMPORTANT);
        event.setEventType(eventType);

        assertEquals(Severity.IMPORTANT, severityTransformer.getSeverity(event));
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
        singleEvent.setEventType(DEFAULT_EVENT_TYPE);

        assertEquals(Severity.CRITICAL, severityTransformer.getSeverity(singleEvent));

        // Multiple events, risk as integer
        Action multipleAction = TestHelpers.createAdvisorAction("test-account-id", "deactivated-recommendation");
        Event multipleEvent = new Event();
        multipleEvent.setEventWrapper(new EventWrapperAction(multipleAction));
        multipleEvent.setEventType(DEFAULT_EVENT_TYPE);

        assertEquals(Severity.MODERATE, severityTransformer.getSeverity(multipleEvent));
    }

    /** This is not an expected case, but it should still be handled gracefully. */
    @Test
    public void testOcmPayloadWithInvalidLegacySeverity() {
        Optional<Map<String, Object>> legacySeverity = Optional.of(Map.of(SEVERITY, "this is not a valid severity level"));
        Action action = OcmTestHelpers.createOcmAction("test-cluster-name", "Premium", "System rebooting",
                "System reboot in progress", "test-title", legacySeverity);
        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(action));
        event.setEventType(DEFAULT_EVENT_TYPE);

        assertEquals(Severity.UNDEFINED, severityTransformer.getSeverity(event));
    }
}
