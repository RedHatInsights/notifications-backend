package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.processors.email.EmailPendo;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import com.redhat.cloud.notifications.templates.models.DailyDigestSection;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static org.mockito.Mockito.when;

public abstract class EmailTemplatesRendererHelper {

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
    protected Environment environment;

    @InjectSpy
    protected TemplateService templateService;

    @Inject
    protected BaseTransformer baseTransformer;

    protected String eventTypeDisplayName;

    @BeforeEach
    protected void initData() {
        when(templateService.isSecuredEmailTemplatesEnabled()).thenReturn(useSecuredTemplates());
        templateService.init();
    }

    @AfterEach
    protected void afterEach() {
        when(templateService.isSecuredEmailTemplatesEnabled()).thenReturn(false);
        templateService.init();
    }

    protected String generateEmailSubject(String eventTypeStr, Action action) {
        TemplateDefinition templateDefinition = new TemplateDefinition(IntegrationType.EMAIL_TITLE, getBundle(), getApp(), eventTypeStr);
        return generateEmail(templateDefinition, action, null);
    }

    protected String generateEmailBody(String eventTypeStr, Action action) {
        return generateEmailBody(eventTypeStr, action, null, false);
    }

    protected String generateEmailBody(String eventTypeStr, Action event, EmailPendo pendo, boolean ignoreUserPreferences) {
        TemplateDefinition templateDefinition = new TemplateDefinition(IntegrationType.EMAIL_BODY, getBundle(), getApp(), eventTypeStr);
        return generateEmail(templateDefinition, event, pendo, ignoreUserPreferences);
    }

    protected String generateAggregatedEmailBody(Map<String, Object> context) {
        return generateAggregatedEmailBody(context, null);
    }

    protected String generateAggregatedEmailBody(Map<String, Object> originalContext, EmailPendo emailPendo) {
        TemplateDefinition templateDefinition = new TemplateDefinition(IntegrationType.EMAIL_DAILY_DIGEST_BODY, getBundle(), getApp(), null);
        Map<String, Object> context = new HashMap<>(originalContext); // to patch immutable maps from individual test cases
        context.put("application", getApp());

        String applicationSectionResult = generateEmailFromContextMap(templateDefinition, context, emailPendo);
        String titleData = applicationSectionResult.split("<!-- Body section -->")[0];
        // Some application daily template such as Inventory can contain several sections
        // each section have a "Jump to details" link that have to be added in the top of aggregated daily digest email
        String[] sections = titleData.split("<!-- next section -->");

        DailyDigestSection dailyDigestSection = new DailyDigestSection(
            applicationSectionResult.split("<!-- Body section -->")[1],
            Arrays.stream(sections).filter(e -> !e.isBlank()).toList());

        Map<String, Object> mapData = Map.of("title", "Daily digest - Red Hat Enterprise Linux", "items", List.of(dailyDigestSection), "orgId", DEFAULT_ORG_ID);
        TemplateDefinition globalDailyTemplateDefinition = new TemplateDefinition(IntegrationType.EMAIL_DAILY_DIGEST_BUNDLE_AGGREGATION_BODY, null, null, null);
        return generateEmailFromContextMap(globalDailyTemplateDefinition, mapData, emailPendo);
    }

    protected String generateAggregatedEmailBody(Action action) {
        return generateAggregatedEmailBody(templateService.convertActionToContextMap(action), null);
    }

    protected String generateEmail(TemplateDefinition templateDefinition, Action action, EmailPendo pendo) {
        return generateEmail(templateDefinition, action, pendo, false);
    }

    protected String generateEmail(TemplateDefinition templateDefinition, Object action, EmailPendo emailPendo, boolean ignoreUserPreferences) {
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("environment", environment);
        additionalContext.put("pendo_message", emailPendo);
        additionalContext.put("ignore_user_preferences", ignoreUserPreferences);
        additionalContext.put("action", action);
        additionalContext.put("source", getSourceEntry());

        String result = templateService.renderTemplateWithCustomDataMap(templateDefinition, additionalContext);
        writeOrSendEmailTemplate(result, templateService.getTemplateId(templateDefinition) + ".html");
        return result;
    }

    protected String generateEmailFromContextMap(TemplateDefinition templateDefinition, Map<String, Object> context, EmailPendo emailPendo) {
        Map<String, Object> action =  Map.of("context", context, "bundle", getBundle(), "timestamp", LocalDateTime.now());

        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("environment", environment);
        additionalContext.put("pendo_message", emailPendo);
        additionalContext.put("ignore_user_preferences", false);
        additionalContext.put("action", action);

        String result = templateService.renderTemplateWithCustomDataMap(templateDefinition, additionalContext);

        writeOrSendEmailTemplate(result, templateService.getTemplateId(templateDefinition) + ".html");

        return result;
    }

    private JsonObject getSourceEntry() {
        Event event = new Event();
        event.setBundleDisplayName(getBundleDisplayName());
        event.setApplicationDisplayName(getAppDisplayName());
        event.setEventTypeDisplayName(eventTypeDisplayName);
        return BaseTransformer.getEventSource(event);
    }

    protected String getBundle() {
        return BUNDLE_RHEL;
    }

    protected String getBundleDisplayName() {
        return "Red Hat Enterprise Linux";
    }

    protected String getAppDisplayName() {
        return null;
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
}
