package com.redhat.cloud.notifications.qute.templates.mapping;

import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.DRAWER;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_DAILY_DIGEST_BODY;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.GOOGLE_CHAT;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.MS_TEAMS;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.SLACK;
import static java.util.Map.entry;

public class DefaultTemplates {

    public static final Map<TemplateDefinition, String> templatesMap = Map.ofEntries(
        // Default Drawer template
        entry(new TemplateDefinition(DRAWER, null, null, null), "Default/defaultBody.md"),
        entry(new TemplateDefinition(MS_TEAMS, null, null, null), "Default/default.json"),
        entry(new TemplateDefinition(GOOGLE_CHAT, null, null, null), "Default/default.json"),
        entry(new TemplateDefinition(SLACK, null, null, null), "Default/default.md"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, null, null, null), "Common/insightsDailyEmailBody.html")
    );
}
