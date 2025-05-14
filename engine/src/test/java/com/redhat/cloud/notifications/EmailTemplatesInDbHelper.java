package com.redhat.cloud.notifications;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.NotificationsConsoleCloudEvent;
import com.redhat.cloud.notifications.models.Template;
import com.redhat.cloud.notifications.processors.email.EmailPendo;
import com.redhat.cloud.notifications.templates.EmailTemplateMigrationService;
import com.redhat.cloud.notifications.templates.TemplateService;
import com.redhat.cloud.notifications.templates.models.DailyDigestSection;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

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

    @InjectMock
    EngineConfig engineConfig;

    @Inject
    EmailTemplateMigrationService emailTemplateMigrationService;

    @Inject
    ObjectMapper objectMapper;

    protected final Map<String, UUID> eventTypes = new HashMap<>();

    @BeforeEach
    protected void initData() {
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
        when(engineConfig.isSecuredEmailTemplatesEnabled()).thenReturn(useSecuredTemplates());
        when(engineConfig.isUseOCMRefactoredTemplates()).thenReturn(useOcmRefactoredTemplates());
        if (engineConfig.isSecuredEmailTemplatesEnabled() || engineConfig.isUseOCMRefactoredTemplates()) {
            emailTemplateMigrationService.deleteAllTemplates();
        }
        migrate();
    }

    @AfterEach
    protected void cleanUp() {
        resourceHelpers.cleanBundleAndApps();
    }

    protected void migrate() {
        emailTemplateMigrationService.migrate();
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
        return generateEmail(subjectTemplate, event, null);
    }

    protected String generateEmailBody(String eventTypeStr, Action action) {
        return generateEmailBody(eventTypeStr, action, false);
    }

    protected String generateEmailBody(String eventTypeStr, Action action, boolean ignoreUserPreferences) {
        return generateEmailBody(eventTypeStr, (Object) action, ignoreUserPreferences);
    }

    protected String generateEmailBody(String eventTypeStr, Action action, EmailPendo pendo) {
        return generateEmailBody(eventTypeStr, (Object) action, pendo);
    }

    protected String generateEmailBody(String eventTypeStr, NotificationsConsoleCloudEvent event) {
        return generateEmailBody(eventTypeStr, (Object) event);
    }

    private String generateEmailBody(String eventTypeStr, Object event, EmailPendo pendo) {
        return generateEmailBody(eventTypeStr, event, pendo, false);
    }

    private String generateEmailBody(String eventTypeStr, Object event, EmailPendo pendo, boolean ignoreUserPreferences) {
        InstantEmailTemplate emailTemplate = templateRepository.findInstantEmailTemplate(eventTypes.get(eventTypeStr)).get();
        TemplateInstance bodyTemplate = templateService.compileTemplate(emailTemplate.getBodyTemplate().getData(), emailTemplate.getBodyTemplate().getName());
        return generateEmail(bodyTemplate, event, pendo, ignoreUserPreferences);
    }

    private String generateEmailBody(String eventTypeStr, Object event) {
        return generateEmailBody(eventTypeStr, event, false);
    }

    private String generateEmailBody(String eventTypeStr, Object event, boolean ignoreUserPreferences) {
        return generateEmailBody(eventTypeStr, event, null, ignoreUserPreferences);
    }

    protected String generateAggregatedEmailBody(Map<String, Object> context) {
        return generateAggregatedEmailBody(context, (EmailPendo) null);
    }

    /** Additionally specifies an account number that would be included with the email event. */
    protected String generateAggregatedEmailBody(Map<String, Object> originalContext, String accountId) {
        return generateAggregatedEmailBody(originalContext, null, accountId);
    }

    protected String generateAggregatedEmailBody(Map<String, Object> originalContext, EmailPendo emailPendo) {
        return generateAggregatedEmailBody(originalContext, emailPendo, null);
    }

    protected String generateAggregatedEmailBody(Map<String, Object> originalContext, EmailPendo emailPendo, String accountId) {
        Map<String, Object> context = new HashMap<>(originalContext); // to patch immutable maps from individual test cases
        context.put("application", getApp());
        AggregationEmailTemplate emailTemplate = templateRepository.findAggregationEmailTemplate(getBundle(), getApp(), DAILY).get();
        TemplateInstance bodyTemplate = templateService.compileTemplate(emailTemplate.getBodyTemplate().getData(), emailTemplate.getBodyTemplate().getName());
        String applicationSectionResult = generateEmailFromContextMap(bodyTemplate, context, emailPendo, bodyTemplate.getTemplate().getId() + "Intermediate");

        String commonTemplatePrefix = bodyTemplate.getTemplate().getId().contains("Secure") ? "Secure/" : "";
        Optional<Template> dailyTemplate = templateRepository.findTemplateByName(commonTemplatePrefix + "Common/insightsDailyEmailBody");
        assertTrue(dailyTemplate.isPresent());

        String titleData = applicationSectionResult.split("<!-- Body section -->")[0];

        // Some application daily template such as Inventory can contain several sections
        // each section have a "Jump to details" link that have to be added in the top of aggregated daily digest email
        String[] sections = titleData.split("<!-- next section -->");

        DailyDigestSection dailyDigestSection = new DailyDigestSection(
            applicationSectionResult.split("<!-- Body section -->")[1],
            Arrays.stream(sections).filter(e -> !e.isBlank()).toList());

        TemplateInstance bodyTemplateGlobalDailyDigest = templateService.compileTemplate(dailyTemplate.get().getData(), "singleDailyDigest/dailyDigest");

        Map<String, Object> mapData = new HashMap<>(Map.of("title", "Daily digest - Red Hat Enterprise Linux", "items", List.of(dailyDigestSection), "orgId", DEFAULT_ORG_ID));
        if (accountId != null && !accountId.isBlank()) {
            mapData.put("accountId", accountId);
        }
        return generateEmailFromContextMap(bodyTemplateGlobalDailyDigest, mapData, emailPendo, bodyTemplate.getTemplate().getId());
    }

    protected String generateAggregatedEmailBody(Action action) {
        Map<String, Object> contextMap = objectMapper
            .convertValue(action.getContext(), new TypeReference<Map<String, Object>>() { });

        return generateAggregatedEmailBody(contextMap, (EmailPendo) null);
    }

    protected String generateEmail(TemplateInstance template, Object actionOrEvent, EmailPendo pendo) {
        return generateEmail(template, actionOrEvent, pendo, false);
    }

    protected String generateEmail(TemplateInstance template, Object actionOrEvent, EmailPendo pendo, boolean ignoreUserPreferences) {

        String result = templateService.renderEmailBodyTemplate(actionOrEvent, template, pendo, ignoreUserPreferences);
        writeOrSendEmailTemplate(result, template.getTemplate().getId() + ".html");

        return result;
    }

    protected String generateEmailFromContextMap(TemplateInstance templateInstance, Map<String, Object> context, EmailPendo emailPendo) {
        Map<String, Object> action =  Map.of("context", context, "bundle", getBundle(), "timestamp", LocalDateTime.now());

        String result = templateService.renderEmailBodyTemplate(action, templateInstance, emailPendo, false);
        writeOrSendEmailTemplate(result, templateInstance.getTemplate().getId() + ".html");

        return result;
    }

    protected String generateEmailFromContextMap(TemplateInstance templateInstance, Map<String, Object> context, EmailPendo emailPendo, String overrideTemplateId) {
        Map<String, Object> action =  Map.of("context", context, "bundle", getBundle(), "timestamp", LocalDateTime.now());

        String result = templateService.renderEmailBodyTemplate(action, templateInstance, emailPendo, false);
        writeOrSendEmailTemplate(result, overrideTemplateId + ".html");

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

    protected Boolean useOcmRefactoredTemplates() {
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
}
