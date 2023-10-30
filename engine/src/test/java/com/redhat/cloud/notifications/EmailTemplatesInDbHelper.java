package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.NotificationsConsoleCloudEvent;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.templates.EmailTemplateMigrationService;
import com.redhat.cloud.notifications.templates.TemplateService;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;

public abstract class EmailTemplatesInDbHelper {

    protected static final String BUNDLE_RHEL = "rhel";

    protected static final String COMMON_SECURED_LABEL_CHECK = "common template for secured env";

    private static final boolean SHOULD_WRITE_ON_FILE_FOR_DEBUG = true;
    private static final boolean SHOULD_SEND_EMAIL_FOR_DEBUG = false;

    private static final String SEND_EMAIL_TO = "test.email@replace.me";

    @Inject
    Mailer mailer;

    @Inject
    protected ResourceHelpers resourceHelpers;

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
            Application application = resourceHelpers.findApp(getBundle(), getApp());
            EventType eventType;
            try {
                eventType = resourceHelpers.findEventType(application.getId(), eventTypeToCreate);
            } catch (NoResultException nre) {
                eventType = resourceHelpers.createEventType(application.getId(), eventTypeToCreate);
            }
            eventTypes.put(eventTypeToCreate, eventType.getId());
        }
        featureFlipper.setUseSecuredEmailTemplates(useSecuredTemplates());
        if (featureFlipper.isUseSecuredEmailTemplates()) {
            emailTemplateMigrationService.deleteAllTemplates();
        }
        migrate();
    }

    protected void migrate() {
        emailTemplateMigrationService.migrate();
    }

    @AfterEach
    void restoreEnv() {
        featureFlipper.setUseSecuredEmailTemplates(false);
    }

    protected String generateEmailSubject(String eventTypeStr, Action action) {
        return generateEmailSubject(eventTypeStr, (Object) action);
    }

    protected String generateEmailSubject(String eventTypeStr, NotificationsConsoleCloudEvent event) {
        return generateEmailSubject(eventTypeStr, (Object) event);
    }

    private String generateEmailSubject(String eventTypeStr, Object event) {
        InstantEmailTemplate emailTemplate = templateRepository.findInstantEmailTemplate(eventTypes.get(eventTypeStr)).get();
        TemplateInstance subjectTemplate = templateService.compileTemplate(emailTemplate.getSubjectTemplate().getData(), emailTemplate.getSubjectTemplate().getName());
        return generateEmail(subjectTemplate, event);
    }

    protected String generateEmailBody(String eventTypeStr, Action action) {
        return generateEmailBody(eventTypeStr, (Object) action);
    }

    protected String generateEmailBody(String eventTypeStr, NotificationsConsoleCloudEvent event) {
        return generateEmailBody(eventTypeStr, (Object) event);
    }

    private String generateEmailBody(String eventTypeStr, Object event) {
        InstantEmailTemplate emailTemplate = templateRepository.findInstantEmailTemplate(eventTypes.get(eventTypeStr)).get();
        TemplateInstance bodyTemplate = templateService.compileTemplate(emailTemplate.getBodyTemplate().getData(), emailTemplate.getBodyTemplate().getName());
        return generateEmail(bodyTemplate, event);
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

    protected String generateEmail(TemplateInstance template, Object actionOrEvent) {

        String result = templateService.renderTemplate(createUser(), actionOrEvent, template);
        writeOrSendEmailTemplate(result, template.getTemplate().getId() + ".html");

        return result;
    }

    protected String generateEmail(TemplateInstance templateInstance, Map<String, Object> context) {
        Map<String, Object> action =  Map.of("context", context, "bundle", getBundle(), "timestamp", LocalDateTime.now());

        String result = templateService.renderTemplate(createUser(), action, templateInstance);
        writeOrSendEmailTemplate(result, templateInstance.getTemplate().getId() + ".html");

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

    protected void writeOrSendEmailTemplate(String result, String fileName) {
        if (SHOULD_WRITE_ON_FILE_FOR_DEBUG) {
            TestHelpers.writeEmailTemplate(result, fileName + UUID.randomUUID() + ".html");
        }

        if (SHOULD_SEND_EMAIL_FOR_DEBUG) {
            Mail m = Mail.withHtml(SEND_EMAIL_TO,
                fileName,
                result
            );
            mailer.send(m);
        }
    }

    protected List<String> getUsedEventTypeNames() {
        return List.of();
    }

    private User createUser() {
        User user = new User();
        user.setFirstName("John");
        user.setLastName("Doe");

        return user;
    }
}
