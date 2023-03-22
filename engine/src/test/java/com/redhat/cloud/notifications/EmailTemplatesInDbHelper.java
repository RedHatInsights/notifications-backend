package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.templates.EmailTemplateMigrationService;
import com.redhat.cloud.notifications.templates.TemplateService;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;

public abstract class EmailTemplatesInDbHelper {

    protected static final String BUNDLE_RHEL = "rhel";

    protected static final String COMMON_SECURED_LABEL_CHECK = "common template for secured env";

    private static final boolean SHOULD_WRITE_ON_FILE_FOR_DEBUG = true;

    @Inject
    Environment environment;

    @Inject
    protected ResourceHelpers resourceHelpers;

    @Inject
    protected StatelessSessionFactory statelessSessionFactory;

    @InjectSpy
    protected TemplateRepository templateRepository;

    @Inject
    protected TemplateService templateService;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    EmailTemplateMigrationService emailTemplateMigrationService;

    protected final Map<String, UUID> eventTypes = new HashMap<>();

    @BeforeEach
    void initData() {
        Bundle bundle = null;
        try {
            bundle = resourceHelpers.findBundle(getBundle());
        } catch (NoResultException nre) {
            bundle = resourceHelpers.createBundle(getBundle());
        }

        try {
            resourceHelpers.findApp(getBundle(), getApp());
        } catch (NoResultException nre) {
            resourceHelpers.createApp(bundle.getId(), getApp());
        }
        for (String eventTypeToCreate : getUsedEventTypeNames()) {
            Application app = resourceHelpers.findApp(getBundle(), getApp());
            EventType eventType;
            try {
                eventType = resourceHelpers.findEventType(advisorOpenshift.getId(), eventTypeToCreate);
            } catch (NoResultException nre) {
                eventType = resourceHelpers.createEventType(advisorOpenshift.getId(), eventTypeToCreate);
            }
            eventTypes.put(eventTypeToCreate, eventType.getId());
        }
        featureFlipper.setUseSecuredEmailTemplates(useSecuredTemplates());
        if (featureFlipper.isUseSecuredEmailTemplates()) {
            emailTemplateMigrationService.deleteAllTemplates();
        }
        migrate();
        featureFlipper.setUseTemplatesFromDb(true);
    }

    protected void migrate() {
        emailTemplateMigrationService.migrate();
    }

    @AfterEach
    void restoreEnv() {
        featureFlipper.setUseSecuredEmailTemplates(false);
        featureFlipper.setUseTemplatesFromDb(false);
    }

    protected String generateEmailSubject(String eventTypeStr, Action action) {
        InstantEmailTemplate emailTemplate = templateRepository.findInstantEmailTemplate(eventTypes.get(eventTypeStr)).get();
        TemplateInstance subjectTemplate = templateService.compileTemplate(emailTemplate.getSubjectTemplate().getData(), emailTemplate.getSubjectTemplate().getName());
        return generateEmail(subjectTemplate, action);
    }

    protected String generateEmailBody(String eventTypeStr, Action action) {
        InstantEmailTemplate emailTemplate = templateRepository.findInstantEmailTemplate(eventTypes.get(eventTypeStr)).get();
        TemplateInstance bodyTemplate = templateService.compileTemplate(emailTemplate.getBodyTemplate().getData(), emailTemplate.getBodyTemplate().getName());
        return generateEmail(bodyTemplate, action);
    }

    protected String generateAggregatedEmailSubject(Map<String, Object> context) {
        AggregationEmailTemplate emailTemplate = templateRepository.findAggregationEmailTemplate(getBundle(), getApp(), DAILY).get();
        TemplateInstance subjectTemplate = templateService.compileTemplate(emailTemplate.getSubjectTemplate().getData(), emailTemplate.getSubjectTemplate().getName());
        return generateEmail(subjectTemplate, context);
    }

    protected String generateAggregatedEmailSubject(Action action) {
        AggregationEmailTemplate emailTemplate = templateRepository.findAggregationEmailTemplate(getBundle(), getApp(), DAILY).get();
        TemplateInstance subjectTemplate = templateService.compileTemplate(emailTemplate.getSubjectTemplate().getData(), emailTemplate.getSubjectTemplate().getName());
        return generateEmail(subjectTemplate, action);
    }

    protected String generateAggregatedEmailBody(Map<String, Object> context) {
        AggregationEmailTemplate emailTemplate = templateRepository.findAggregationEmailTemplate(getBundle(), getApp(), DAILY).get();
        TemplateInstance bodyTemplate = templateService.compileTemplate(emailTemplate.getBodyTemplate().getData(), emailTemplate.getBodyTemplate().getName());
        return generateEmail(bodyTemplate, context);
    }

    protected String generateAggregatedEmailBody(Action action) {
        AggregationEmailTemplate emailTemplate = templateRepository.findAggregationEmailTemplate(getBundle(), getApp(), DAILY).get();
        TemplateInstance bodyTemplate = templateService.compileTemplate(emailTemplate.getBodyTemplate().getData(), emailTemplate.getBodyTemplate().getName());
        return generateEmail(bodyTemplate, action);
    }

    protected String generateEmail(TemplateInstance template, Action action) {
        String result = template
            .data("action", action)
            .data("environment", environment)
            .data("user", Map.of("firstName", "John", "lastName", "Doe"))
            .render();

        writeEmailTemplate(result, template.getTemplate().getId() + ".html");

        return result;
    }

    protected String generateEmail(TemplateInstance templateInstance, Map<String, Object> context) {
        String result = templateInstance
            .data("action", Map.of("context", context, "bundle", getBundle(), "timestamp", LocalDateTime.now()))
            .data("environment", environment)
            .data("user", Map.of("firstName", "John", "lastName", "Doe"))
            .render();

        writeEmailTemplate(result, templateInstance.getTemplate().getId() + ".html");

        return result;
    }

    protected String getBundle() {
        return BUNDLE_RHEL;
    }

    protected String getApp() {
        return null;
    }

    protected Boolean useSecuredTemplates() {
        return false;
    }

    protected void writeEmailTemplate(String result, String fileName) {
        if (SHOULD_WRITE_ON_FILE_FOR_DEBUG) {
            TestHelpers.writeEmailTemplate(result, fileName);
        }
    }

    protected List<String> getUsedEventTypeNames() {
        return List.of();
    }
}
