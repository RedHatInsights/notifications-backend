package com.redhat.cloud.notifications.qute.templates.mapping;

import com.redhat.cloud.notifications.qute.templates.InstantTemplateDetails;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InstantLabelMappingTest {

    @Test
    void retiringLifecycleEventReturnsMonthlyNotification() {
        InstantTemplateDetails details = InstantLabelMapping.buildInstantNotificationDescription("rhel", "life-cycle", "retiring-lifecycle");

        assertEquals("Monthly notification", details.label());
        assertEquals("Monthly summary of triggered application events.", details.description());
        assertNull(details.checkedWarning());
    }

    @Test
    void nonRetiringLifecycleEventReturnsInstantNotification() {
        InstantTemplateDetails details = InstantLabelMapping.buildInstantNotificationDescription("rhel", "life-cycle", "other-event-type");

        assertEquals("Instant notification", details.label());
        assertEquals("Immediate email for each triggered application event.", details.description());
        assertEquals("Opting into this notification may result in a large number of emails", details.checkedWarning());
    }

    @Test
    void roadmapMonthlyReportEventReturnsMonthlyNotification() {
        InstantTemplateDetails details = InstantLabelMapping.buildInstantNotificationDescription("rhel", "roadmap", "roadmap-monthly-report");

        assertEquals("Monthly notification", details.label());
        assertEquals("Monthly summary of triggered application events.", details.description());
        assertNull(details.checkedWarning());
    }

    @Test
    void nonMonthlyReportRoadmapEventReturnsInstantNotification() {
        InstantTemplateDetails details = InstantLabelMapping.buildInstantNotificationDescription("rhel", "roadmap", "other-event-type");

        assertEquals("Instant notification", details.label());
        assertEquals("Immediate email for each triggered application event.", details.description());
        assertEquals("Opting into this notification may result in a large number of emails", details.checkedWarning());
    }

    @Test
    void nonLifecycleApplicationReturnsInstantNotification() {
        InstantTemplateDetails details = InstantLabelMapping.buildInstantNotificationDescription("rhel", "policies", "policy-triggered");

        assertEquals("Instant notification", details.label());
        assertEquals("Immediate email for each triggered application event.", details.description());
        assertEquals("Opting into this notification may result in a large number of emails", details.checkedWarning());
    }

    @Test
    void nonRhelBundleReturnsInstantNotification() {
        InstantTemplateDetails details = InstantLabelMapping.buildInstantNotificationDescription("openshift", "some-app", "some-event");

        assertEquals("Instant notification", details.label());
        assertEquals("Immediate email for each triggered application event.", details.description());
        assertEquals("Opting into this notification may result in a large number of emails", details.checkedWarning());
    }
}
