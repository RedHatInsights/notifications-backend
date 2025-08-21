package email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import email.pojo.DailyDigestSection;
import email.pojo.EmailPendo;
import email.pojo.Environment;
import io.quarkus.logging.Log;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
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

    ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    Mailer mailer;

    @Inject
    Environment environment;

    @InjectSpy
    protected TemplateService templateService;

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

    protected String generateAggregatedEmailBody(String jsonContext) throws JsonProcessingException {
        return generateAggregatedEmailBody(objectMapper.readValue(jsonContext, new TypeReference<Map<String, Object>>() { }));
    }

    protected String generateAggregatedEmailBody(Map<String, Object> context) {
        return generateAggregatedEmailBody(context, null);
    }

    protected String generateAggregatedEmailBody(Map<String, Object> originalContext, EmailPendo emailPendo) {
        TemplateDefinition templateDefinition = new TemplateDefinition(IntegrationType.EMAIL_DAILY_DIGEST_BODY, getBundle(), getApp(), null, true);
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
        // JSON property names' definition.
        final String APPLICATION = "application";
        final String BUNDLE = "bundle";
        final String DISPLAY_NAME = "display_name";
        final String EVENT_TYPE = "event_type";

        final JsonObject source = new JsonObject();

        final JsonObject sourceAppDisplayName = new JsonObject();
        sourceAppDisplayName.put(DISPLAY_NAME, getAppDisplayName());
        source.put(APPLICATION, sourceAppDisplayName);

        final JsonObject sourceBundleDisplayName = new JsonObject();
        sourceBundleDisplayName.put(DISPLAY_NAME, getBundleDisplayName());
        source.put(BUNDLE, sourceBundleDisplayName);

        final JsonObject sourceEventTypeDisplayName = new JsonObject();
        sourceEventTypeDisplayName.put(DISPLAY_NAME, eventTypeDisplayName);
        source.put(EVENT_TYPE, sourceEventTypeDisplayName);

        return source;
    }

    private void addItem(Map<String, DailyDigestSection> dataMap, String applicationName, String payload) {
        String titleData = payload.split("<!-- Body section -->")[0];
        String[] sections = titleData.split("<!-- next section -->");

        dataMap.put(applicationName,
            new DailyDigestSection(payload.split("<!-- Body section -->")[1], Arrays.stream(sections)
                .filter(e -> !e.isBlank()).toList()));
    }

    protected void generateAggregatedEmailBody(String jsonContext, String app, Map<String, DailyDigestSection> dataMap) throws JsonProcessingException {
        generateAggregatedEmailBody(
                objectMapper.readValue(jsonContext, new TypeReference<Map<String, Object>>() { }),
                app,
                dataMap);
    }

    protected void generateAggregatedEmailBody(Map<String, Object> context, String app, Map<String, DailyDigestSection> dataMap) {
        context.put("application", app);
        TemplateDefinition templateDefinition = new TemplateDefinition(IntegrationType.EMAIL_DAILY_DIGEST_BODY, getBundle(), app, null, true);

        addItem(dataMap, app, generateEmailFromContextMap(templateDefinition, context, null));
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

    protected void writeOrSendEmailTemplate(String result, String fileName) {
        if (SHOULD_WRITE_ON_FILE_FOR_DEBUG) {
            writeEmailTemplate(result, fileName + UUID.randomUUID() + ".html");
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

    public static void writeEmailTemplate(String result, String fileName) {
        final String TARGET_DIR = "target";
        try {
            String[] splitPath = fileName.split("/");
            String actualPath = TARGET_DIR;
            for (int part = 0; part < splitPath.length - 1; part++) {
                actualPath += "/" + splitPath[part];
            }
            Files.createDirectories(Paths.get(actualPath));
            Files.write(Paths.get(TARGET_DIR + "/" + fileName), result.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.error("An error occurred", e);
        }
    }
}
