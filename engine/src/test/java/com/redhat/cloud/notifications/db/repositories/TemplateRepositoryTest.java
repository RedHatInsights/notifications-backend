package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.Template;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TemplateRepositoryTest {

    @Inject
    TemplateRepository templateRepository;

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

        subjectTemplate = resourceHelpers.createTemplate("subject-template-" + UUID.randomUUID(), "Subject template", "You have a notification!");
        bodyTemplate = resourceHelpers.createTemplate("body-template-" + UUID.randomUUID(), "Body template", "Something happened!");
    }

    @Test
    void testIsEmailAggregationSupported() {
        // First, email aggregation is not supported by any application.
        assertIsEmailAggregationSupported(false, false);

        // Then we link an aggregation (DAILY) email template with app-1.
        AggregationEmailTemplate createdTemplate = resourceHelpers.createAggregationEmailTemplate(app1.getId(), subjectTemplate.getId(), bodyTemplate.getId(), true);
        invalidateIsEmailAggregationSupportedCache(app1.getId());

        /*
         * Expectations:
         * - app-1 should now support email aggregation
         * - app-2 should still not support email aggregation
         */
        assertIsEmailAggregationSupported(true, false);
        resourceHelpers.deleteEmailTemplatesById(createdTemplate.getId());
    }

    @CacheInvalidate(cacheName = "is-email-aggregation-supported")
    void invalidateIsEmailAggregationSupportedCache(UUID appId) {
    }

    private void assertIsEmailAggregationSupported(boolean app1, boolean app2) {
        assertEquals(app1, templateRepository.isEmailAggregationSupported(this.app1.getId()));
        assertEquals(app2, templateRepository.isEmailAggregationSupported(this.app2.getId()));
    }

    @Test
    void testFindInstantEmailTemplate() {
        // First, none of the event types are linked with an instant email template.
        assertTrue(templateRepository.findInstantEmailTemplate(eventType1.getId()).isEmpty());
        assertTrue(templateRepository.findInstantEmailTemplate(eventType2.getId()).isEmpty());

        // Then we link an instant email template with event-type-1...
        InstantEmailTemplate createdTemplate = resourceHelpers.createInstantEmailTemplate(eventType1.getId(), subjectTemplate.getId(), bodyTemplate.getId(), true);
        invalidateInstantEmailTemplatesCache(eventType1.getId());
        // ... and retrieve it from the DB using the repository.
        Optional<InstantEmailTemplate> instantTemplate = templateRepository.findInstantEmailTemplate(eventType1.getId());

        // The retrieved instant email template should contain the subject/body templates that were created with the helper.
        assertEquals(subjectTemplate, instantTemplate.get().getSubjectTemplate());
        assertEquals(bodyTemplate, instantTemplate.get().getBodyTemplate());

        // event-type-2 should still not be linked to any instant email template.
        assertTrue(templateRepository.findInstantEmailTemplate(eventType2.getId()).isEmpty());

        resourceHelpers.deleteEmailTemplatesById(createdTemplate.getId());
    }

    @CacheInvalidate(cacheName = "instant-email-templates")
    void invalidateInstantEmailTemplatesCache(UUID eventTypeId) {
    }

    @Test
    void testFindAggregationEmailTemplate() {
        // First, none of the applications are linked with an aggregation email template.
        assertTrue(templateRepository.findAggregationEmailTemplate(bundle.getName(), app1.getName(), DAILY).isEmpty());
        assertTrue(templateRepository.findAggregationEmailTemplate(bundle.getName(), app2.getName(), DAILY).isEmpty());

        // Then we link an aggregation email template with app-1...
        AggregationEmailTemplate createdTemplate = resourceHelpers.createAggregationEmailTemplate(app1.getId(), subjectTemplate.getId(), bodyTemplate.getId(), true);
        // ... and retrieve it from the DB using the repository.
        Optional<AggregationEmailTemplate> aggregationTemplate = templateRepository.findAggregationEmailTemplate(bundle.getName(), app1.getName(), DAILY);

        // The retrieved aggregation email template should contain the subject/body templates that were created with the helper.
        assertEquals(subjectTemplate, aggregationTemplate.get().getSubjectTemplate());
        assertEquals(bodyTemplate, aggregationTemplate.get().getBodyTemplate());

        // app-2 should still not be linked to any aggregation email template.
        assertTrue(templateRepository.findAggregationEmailTemplate(bundle.getName(), app2.getName(), DAILY).isEmpty());

        resourceHelpers.deleteEmailTemplatesById(createdTemplate.getId());
    }
}
