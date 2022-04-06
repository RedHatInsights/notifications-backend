package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.Template;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TemplateRepositoryTest {

    @Inject
    TemplateRepository templateRepository;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @Inject
    ResourceHelpers resourceHelpers;

    private Bundle bundle;
    private Application app1;
    private Application app2;
    private EventType eventType1;
    private EventType eventType2;
    private Template subjectTemplate;
    private Template bodyTemplate;

    @BeforeEach
    void beforeEach() {
        bundle = resourceHelpers.createBundle("bundle-" + UUID.randomUUID());

        app1 = resourceHelpers.createApp(bundle.getId(), "app-1-" + UUID.randomUUID());
        eventType1 = resourceHelpers.createEventType(app1.getId(), "event-type-1-" + UUID.randomUUID());

        app2 = resourceHelpers.createApp(bundle.getId(), "app-2-" + UUID.randomUUID());
        eventType2 = resourceHelpers.createEventType(app2.getId(), "event-type-2-" + UUID.randomUUID());

        subjectTemplate = resourceHelpers.createTemplate("subject-template", "Subject template", "You have a notification!");
        bodyTemplate = resourceHelpers.createTemplate("body-template", "Body template", "Something happened!");
    }

    @Test
    void testIsEmailSubscriptionSupported() {
        statelessSessionFactory.withSession(statelessSession -> {
            // First, email subscription is not supported by any application.
            assertIsEmailSubscriptionSupported(false, false, false, false, false, false);

            // Then we link an instant email template with event-type-1 (which is a child entity of app-1).
            resourceHelpers.createInstantEmailTemplate(eventType1.getId(), subjectTemplate.getId(), bodyTemplate.getId(), true);

            /*
             * Expectations:
             * - app-1 should now support INSTANT email subscription, but not DAILY
             * - other applications shouldn't support any kind of email subscription
             */
            assertIsEmailSubscriptionSupported(true, false, false, false, false, false);

            // Then we link an aggregation (DAILY) email template with event-type2 (which is a child entity of app-2).
            resourceHelpers.createAggregationEmailTemplate(app2.getId(), subjectTemplate.getId(), bodyTemplate.getId(), true);

            /*
             * Expectations:
             * - app-1 should now support INSTANT email subscription, but not DAILY
             * - app-2 should now support DAILY email subscription, but not INSTANT
             * - other applications shouldn't support any kind of email subscription
             */
            assertIsEmailSubscriptionSupported(true, false, false, true, false, false);

            resourceHelpers.deleteAllEmailTemplates();
        });
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

    @Test
    void testIsEmailAggregationSupported() {
        statelessSessionFactory.withSession(statelessSession -> {
            // First, email aggregation is not supported by any application.
            assertIsEmailAggregationSupported(false, false);

            // Then we link an aggregation (DAILY) email template with app-1.
            resourceHelpers.createAggregationEmailTemplate(app1.getId(), subjectTemplate.getId(), bodyTemplate.getId(), true);

            /*
             * Expectations:
             * - app-1 should now support email aggregation
             * - app-2 should still not support email aggregation
             */
            assertIsEmailAggregationSupported(true, false);

            resourceHelpers.deleteAllEmailTemplates();
        });
    }

    private void assertIsEmailAggregationSupported(boolean app1, boolean app2) {
        assertEquals(app1, templateRepository.isEmailAggregationSupported(bundle.getName(), this.app1.getName(), List.of(DAILY)));
        assertEquals(app2, templateRepository.isEmailAggregationSupported(bundle.getName(), this.app2.getName(), List.of(DAILY)));
    }

    @Test
    void testFindInstantEmailTemplate() {
        statelessSessionFactory.withSession(statelessSession -> {
            // First, none of the event types are linked with an instant email template.
            assertTrue(templateRepository.findInstantEmailTemplate(eventType1.getId()).isEmpty());
            assertTrue(templateRepository.findInstantEmailTemplate(eventType2.getId()).isEmpty());

            // Then we link an instant email template with event-type-1...
            resourceHelpers.createInstantEmailTemplate(eventType1.getId(), subjectTemplate.getId(), bodyTemplate.getId(), true);
            // ... and retrieve it from the DB using the repository.
            Optional<InstantEmailTemplate> instantTemplate = templateRepository.findInstantEmailTemplate(eventType1.getId());

            // The retrieved instant email template should contain the subject/body templates that were created with the helper.
            assertEquals(subjectTemplate, instantTemplate.get().getSubjectTemplate());
            assertEquals(bodyTemplate, instantTemplate.get().getBodyTemplate());

            // event-type-2 should still not be linked to any instant email template.
            assertTrue(templateRepository.findInstantEmailTemplate(eventType2.getId()).isEmpty());

            resourceHelpers.deleteAllEmailTemplates();
        });
    }

    @Test
    void testFindAggregationEmailTemplate() {
        statelessSessionFactory.withSession(statelessSession -> {
            // First, none of the applications are linked with an aggregation email template.
            assertTrue(templateRepository.findAggregationEmailTemplate(bundle.getName(), app1.getName(), DAILY).isEmpty());
            assertTrue(templateRepository.findAggregationEmailTemplate(bundle.getName(), app2.getName(), DAILY).isEmpty());

            // Then we link an aggregation email template with app-1...
            resourceHelpers.createAggregationEmailTemplate(app1.getId(), subjectTemplate.getId(), bodyTemplate.getId(), true);
            // ... and retrieve it from the DB using the repository.
            Optional<AggregationEmailTemplate> aggregationTemplate = templateRepository.findAggregationEmailTemplate(bundle.getName(), app1.getName(), DAILY);

            // The retrieved aggregation email template should contain the subject/body templates that were created with the helper.
            assertEquals(subjectTemplate, aggregationTemplate.get().getSubjectTemplate());
            assertEquals(bodyTemplate, aggregationTemplate.get().getBodyTemplate());

            // app-2 should still not be linked to any aggregation email template.
            assertTrue(templateRepository.findAggregationEmailTemplate(bundle.getName(), app2.getName(), DAILY).isEmpty());

            resourceHelpers.deleteAllEmailTemplates();
        });
    }
}
