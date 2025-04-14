package com.redhat.cloud.notifications.qute.templates.mapping;

import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;

import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.DRAWER;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.GOOGLE_CHAT;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.MS_TEAMS;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.SLACK;
import static java.util.Map.entry;

public class SubscriptionServices {
    static final String BUNDLE_NAME = "subscription-services";

    static final String ERRATA_NOTIFICATIONS_APP_NAME = "errata-notifications";
    static final String ERRATA_NOTIFICATIONS_FOLDER_NAME = "Errata/";

    public static final Map<TemplateDefinition, String> templatesMap = Map.ofEntries(
        // Errata Notifications
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, ERRATA_NOTIFICATIONS_APP_NAME, "new-subscription-bugfix-errata"), ERRATA_NOTIFICATIONS_FOLDER_NAME + "NewSubscriptionBugfixErrata.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, ERRATA_NOTIFICATIONS_APP_NAME, "new-subscription-security-errata"), ERRATA_NOTIFICATIONS_FOLDER_NAME + "NewSubscriptionSecurityErrata.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, ERRATA_NOTIFICATIONS_APP_NAME, "new-subscription-enhancement-errata"), ERRATA_NOTIFICATIONS_FOLDER_NAME + "NewSubscriptionEnhancementErrata.md"),

        entry(new TemplateDefinition(GOOGLE_CHAT, BUNDLE_NAME, ERRATA_NOTIFICATIONS_APP_NAME, "new-subscription-bugfix-errata"), ERRATA_NOTIFICATIONS_FOLDER_NAME + "NewSubscriptionBugfixErrata.json"),
        entry(new TemplateDefinition(GOOGLE_CHAT, BUNDLE_NAME, ERRATA_NOTIFICATIONS_APP_NAME, "new-subscription-security-errata"), ERRATA_NOTIFICATIONS_FOLDER_NAME + "NewSubscriptionSecurityErrata.json"),
        entry(new TemplateDefinition(GOOGLE_CHAT, BUNDLE_NAME, ERRATA_NOTIFICATIONS_APP_NAME, "new-subscription-enhancement-errata"), ERRATA_NOTIFICATIONS_FOLDER_NAME + "NewSubscriptionEnhancementErrata.json"),

        entry(new TemplateDefinition(MS_TEAMS, BUNDLE_NAME, ERRATA_NOTIFICATIONS_APP_NAME, "new-subscription-bugfix-errata"), ERRATA_NOTIFICATIONS_FOLDER_NAME + "NewSubscriptionBugfixErrata.json"),
        entry(new TemplateDefinition(MS_TEAMS, BUNDLE_NAME, ERRATA_NOTIFICATIONS_APP_NAME, "new-subscription-security-errata"), ERRATA_NOTIFICATIONS_FOLDER_NAME + "NewSubscriptionSecurityErrata.json"),
        entry(new TemplateDefinition(MS_TEAMS, BUNDLE_NAME, ERRATA_NOTIFICATIONS_APP_NAME, "new-subscription-enhancement-errata"), ERRATA_NOTIFICATIONS_FOLDER_NAME + "NewSubscriptionEnhancementErrata.json"),

        entry(new TemplateDefinition(SLACK, BUNDLE_NAME, ERRATA_NOTIFICATIONS_APP_NAME, "new-subscription-bugfix-errata"), ERRATA_NOTIFICATIONS_FOLDER_NAME + "NewSubscriptionBugfixErrata.md"),
        entry(new TemplateDefinition(SLACK, BUNDLE_NAME, ERRATA_NOTIFICATIONS_APP_NAME, "new-subscription-security-errata"), ERRATA_NOTIFICATIONS_FOLDER_NAME + "NewSubscriptionSecurityErrata.md"),
        entry(new TemplateDefinition(SLACK, BUNDLE_NAME, ERRATA_NOTIFICATIONS_APP_NAME, "new-subscription-enhancement-errata"), ERRATA_NOTIFICATIONS_FOLDER_NAME + "NewSubscriptionEnhancementErrata.md")
    );
}
