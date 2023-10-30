package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.Template;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.SubscriptionType.INSTANT;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TemplateRepositoryTest extends DbIsolatedTest {

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    TemplateRepository templateRepository;

    private Bundle bundle;
    private Application app1;
    private Application app2;
    private EventType eventType1;
    private Template subjectTemplate;
    private Template bodyTemplate;

    @BeforeEach
    void beforeEach() {
        bundle = resourceHelpers.createBundle("bundle-" + UUID.randomUUID(), "bundle-display-name");

        app1 = resourceHelpers.createApplication(bundle.getId(), "app-1-" + UUID.randomUUID(), "app-1-display-name");
        eventType1 = resourceHelpers.createEventType(app1.getId(), "event-type-1-" + UUID.randomUUID(), "event-type-1-display-name", "event-type-1-description");

        app2 = resourceHelpers.createApplication(bundle.getId(), "app-2-" + UUID.randomUUID(), "app-2-display-name");

        subjectTemplate = resourceHelpers.createTemplate("subject-template-" + UUID.randomUUID(), "Subject template", "You have a notification!");
        bodyTemplate = resourceHelpers.createTemplate("body-template-" + UUID.randomUUID(), "Body template", "Something happened!");
    }

    @Test
    void testIsEmailSubscriptionSupported() {
        // First, email subscription is not supported by any application.
        assertIsEmailSubscriptionSupported(false, false, false, false, false, false);

        // Then we link an instant email template with event-type-1 (which is a child entity of app-1).
        InstantEmailTemplate createdInstanceEmailTemplate = resourceHelpers.createInstantEmailTemplate(eventType1.getId(), subjectTemplate.getId(), bodyTemplate.getId());

        /*
         * Expectations:
         * - app-1 should now support INSTANT email subscription, but not DAILY
         * - other applications shouldn't support any kind of email subscription
         */
        assertIsEmailSubscriptionSupported(true, false, false, false, false, false);

        // Then we link an aggregation (DAILY) email template with event-type2 (which is a child entity of app-2).
        AggregationEmailTemplate createdTemplate = resourceHelpers.createAggregationEmailTemplate(app2.getId(), subjectTemplate.getId(), bodyTemplate.getId());

        /*
         * Expectations:
         * - app-1 should now support INSTANT email subscription, but not DAILY
         * - app-2 should now support DAILY email subscription, but not INSTANT
         * - other applications shouldn't support any kind of email subscription
         */
        assertIsEmailSubscriptionSupported(true, false, false, true, false, false);

        resourceHelpers.deleteEmailTemplatesById(createdTemplate.getId());
        resourceHelpers.deleteEmailTemplatesById(createdInstanceEmailTemplate.getId());
    }

    private void assertIsEmailSubscriptionSupported(boolean app1Instant, boolean app1Daily, boolean app2Instant,
                                                    boolean app2Daily, boolean unknownAppInstant, boolean unknownAppDaily) {

        // bundle / app-1 / event-type-1
        assertEquals(app1Instant, templateRepository.isEmailSubscriptionSupported(bundle.getName(), app1.getName(), INSTANT));
        assertEquals(app1Daily, templateRepository.isEmailSubscriptionSupported(bundle.getName(), app1.getName(), DAILY));

        // bundle / app-2 / event-type-2
        assertEquals(app2Instant, templateRepository.isEmailSubscriptionSupported(bundle.getName(), app2.getName(), INSTANT));
        assertEquals(app2Daily, templateRepository.isEmailSubscriptionSupported(bundle.getName(), app2.getName(), DAILY));

        // unknown-bundle / unknown-app
        assertEquals(unknownAppInstant, templateRepository.isEmailSubscriptionSupported("unknown-bundle", "unknown-app", INSTANT));
        assertEquals(unknownAppDaily, templateRepository.isEmailSubscriptionSupported("unknown-bundle", "unknown-app", DAILY));
    }
}
