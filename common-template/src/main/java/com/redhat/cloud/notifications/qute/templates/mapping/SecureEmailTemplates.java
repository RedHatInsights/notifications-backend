package com.redhat.cloud.notifications.qute.templates.mapping;

import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.*;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.ADVISOR_APP_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.ADVISOR_FOLDER_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.BUNDLE_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.COMPLIANCE_APP_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.COMPLIANCE_FOLDER_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.INVENTORY_APP_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.INVENTORY_FOLDER_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.PATCH_APP_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.PATCH_FOLDER_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.POLICIES_APP_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.POLICY_FOLDER_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.RESOURCE_OPTIMIZATION_APP_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.RESOURCE_OPTIMIZATION_FOLDER_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.VULNERABILITY_APP_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.VULNERABILITY_FOLDER_NAME;
import static java.util.Map.entry;

public class SecureEmailTemplates {

    static final String SECURE_FOLDER_NAME = "Secure/";

    public static final Map<TemplateDefinition, String> templatesMap = Map.ofEntries(
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, ADVISOR_APP_NAME, null), SECURE_FOLDER_NAME + ADVISOR_FOLDER_NAME + "dailyEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, COMPLIANCE_APP_NAME, null), SECURE_FOLDER_NAME + COMPLIANCE_FOLDER_NAME + "dailyEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, INVENTORY_APP_NAME, null), SECURE_FOLDER_NAME + INVENTORY_FOLDER_NAME + "dailyEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, PATCH_APP_NAME, null), SECURE_FOLDER_NAME + PATCH_FOLDER_NAME + "dailyEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, POLICIES_APP_NAME, null), SECURE_FOLDER_NAME + POLICY_FOLDER_NAME + "dailyEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, RESOURCE_OPTIMIZATION_APP_NAME, null), SECURE_FOLDER_NAME + RESOURCE_OPTIMIZATION_FOLDER_NAME + "dailyEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, VULNERABILITY_APP_NAME, null), SECURE_FOLDER_NAME + VULNERABILITY_FOLDER_NAME + "dailyEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BUNDLE_AGGREGATION_BODY, null, null, null), SECURE_FOLDER_NAME + "Common/insightsDailyEmailBody.html")
    );
}
