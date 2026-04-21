package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.Template;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.SubscriptionType.DRAWER;
import static com.redhat.cloud.notifications.models.SubscriptionType.INSTANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TemplateRepositoryTest extends DbIsolatedTest {

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    TemplateRepository templateRepository;

    @InjectMock
    BackendConfig backendConfig;

    private Bundle bundle;
    private Application app1;
    private Application app2;
    private EventType eventType1;
    private EventType eventType2;
    private EventType app2eventType1;
    private Template subjectTemplate;
    private Template bodyTemplate;

    @BeforeEach
    void beforeEach() {
        bundle = resourceHelpers.createBundle("bundle-" + UUID.randomUUID(), "bundle-display-name");

        app1 = resourceHelpers.createApplication(bundle.getId(), "app-1-" + UUID.randomUUID(), "app-1-display-name");
        eventType1 = resourceHelpers.createEventType(app1.getId(), "event-type-1-" + UUID.randomUUID(), "event-type-1-display-name", "event-type-1-description");
        eventType2 = resourceHelpers.createEventType(app1.getId(), "event-type-2-" + UUID.randomUUID(), "event-type-2-display-name", "event-type-2-description");

        app2 = resourceHelpers.createApplication(bundle.getId(), "app-2-" + UUID.randomUUID(), "app-2-display-name");
        app2eventType1 = resourceHelpers.createEventType(app2.getId(), "event-type-1-" + UUID.randomUUID(), "event-type-1-display-name", "event-type-1-description");

        subjectTemplate = resourceHelpers.createTemplate("subject-template-" + UUID.randomUUID(), "Subject template", "You have a notification!");
        bodyTemplate = resourceHelpers.createTemplate("body-template-" + UUID.randomUUID(), "Body template", "Something happened!");
    }

    @Test
    void testIsSubscriptionTypeSupported() {
        // First, email subscription is not supported by any application.
        assertIsSubscriptionTypeSupported(false, false, false, false, false, false);

        // Then we link an instant email template with event-type-1 (which is a child entity of app-1).
        InstantEmailTemplate createdInstanceEmailTemplate = resourceHelpers.createInstantEmailTemplate(eventType1.getId(), subjectTemplate.getId(), bodyTemplate.getId());

        /*
         * Expectations:
         * - app-1 should now support INSTANT email subscription, but not DAILY
         * - other applications shouldn't support any kind of email subscription
         */
        assertIsSubscriptionTypeSupported(true, false, false, false, false, false);

        // Then we link an aggregation (DAILY) email template with event-type2 (which is a child entity of app-2).
        AggregationEmailTemplate createdTemplate = resourceHelpers.createAggregationEmailTemplate(app2.getId(), subjectTemplate.getId(), bodyTemplate.getId());

        /*
         * Expectations:
         * - app-1 should now support INSTANT email subscription, but not DAILY
         * - app-2 should now support DAILY email subscription, but not INSTANT
         * - other applications shouldn't support any kind of email subscription
         */
        assertIsSubscriptionTypeSupported(true, false, false, true, false, false);

        resourceHelpers.deleteEmailTemplatesById(createdTemplate.getId());
        resourceHelpers.deleteEmailTemplatesById(createdInstanceEmailTemplate.getId());
    }

    private void assertIsSubscriptionTypeSupported(boolean app1Instant, boolean app1Daily, boolean app2Instant,
                                                   boolean app2Daily, boolean unknownAppInstant, boolean unknownAppDaily) {

        // bundle / app-1 / event-type-1
        assertEquals(app1Instant, templateRepository.isSubscriptionTypeSupported(eventType1.getId(), INSTANT, DEFAULT_ORG_ID));
        assertFalse(templateRepository.isSubscriptionTypeSupported(eventType2.getId(), INSTANT, DEFAULT_ORG_ID));
        assertEquals(app1Daily, templateRepository.isSubscriptionTypeSupported(eventType1.getId(), DAILY, DEFAULT_ORG_ID));
        assertEquals(app1Daily, templateRepository.isSubscriptionTypeSupported(eventType2.getId(), DAILY, DEFAULT_ORG_ID));

        // bundle / app-2 / event-type-2
        assertEquals(app2Instant, templateRepository.isSubscriptionTypeSupported(app2eventType1.getId(), INSTANT, DEFAULT_ORG_ID));
        assertEquals(app2Daily, templateRepository.isSubscriptionTypeSupported(app2eventType1.getId(), DAILY, DEFAULT_ORG_ID));

        // unknown-bundle / unknown-app
        assertEquals(unknownAppInstant, templateRepository.isSubscriptionTypeSupported(UUID.randomUUID(), INSTANT, DEFAULT_ORG_ID));
        assertEquals(unknownAppDaily, templateRepository.isSubscriptionTypeSupported(UUID.randomUUID(), DAILY, DEFAULT_ORG_ID));
    }

    @Test
    void testIsSubscriptionTypeSupportedOrgSpecific() {
        final String ORG_WITH_DRAWER_ENABLED = "org-with-drawer-enabled";
        final String ORG_WITH_DRAWER_DISABLED = "org-with-drawer-disabled";

        when(backendConfig.isDrawerEnabled(eq(ORG_WITH_DRAWER_ENABLED))).thenReturn(true);
        when(backendConfig.isDrawerEnabled(eq(ORG_WITH_DRAWER_DISABLED))).thenReturn(false);
        when(backendConfig.isDefaultTemplateEnabled()).thenReturn(false);

        // Create drawer template
        resourceHelpers.createDrawerTemplate("Drawer template subscription support", bundle.getName(), app1.getName(), eventType1.getName());

        // Verify drawer supported for org-with-drawer-enabled
        assertTrue(templateRepository.isSubscriptionTypeSupported(
            eventType1.getId(), DRAWER, ORG_WITH_DRAWER_ENABLED
        ), "Drawer should be supported for org-with-drawer-enabled");

        // Verify drawer NOT supported for org-with-drawer-disabled
        assertFalse(templateRepository.isSubscriptionTypeSupported(
            eventType1.getId(), DRAWER, ORG_WITH_DRAWER_DISABLED
        ), "Drawer should NOT be supported for org-with-drawer-disabled");
    }
}
