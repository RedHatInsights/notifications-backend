package com.redhat.cloud.notifications.qute.templates.mapping;

import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;

import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.DRAWER;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_BODY;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_DAILY_DIGEST_BODY;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_TITLE;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.GOOGLE_CHAT;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.MS_TEAMS;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.SLACK;
import static java.util.Map.entry;

public class SubscriptionServices {
    public static final String BUNDLE_NAME = "subscription-services";

    public static final String ERRATA_APP_NAME = "errata-notifications";
    static final String ERRATA_FOLDER_NAME = "Errata/";

    public static final String SUBSCRIPTIONS_USAGE_APP_NAME = "subscriptions";
    static final String SUBSCRIPTIONS_USAGE_FOLDER_NAME = "SubscriptionsUsage/";

    public static final String ERRATA_NEW_SUBSCRIPTION_BUGFIX_ERRATA = "new-subscription-bugfix-errata";
    public static final String ERRATA_NEW_SUBSCRIPTION_SECURITY_ERRATA = "new-subscription-security-errata";
    public static final String ERRATA_NEW_SUBSCRIPTION_ENHANCEMENT_ERRATA = "new-subscription-enhancement-errata";

    public static final String SUBSCRIPTIONS_USAGE_EXCEEDED_UTILIZATION_THRESHOLD = "exceeded-utilization-threshold";

    public static final Map<TemplateDefinition, String> templatesMap = Map.ofEntries(

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_BUGFIX_ERRATA), ERRATA_FOLDER_NAME + "newSubscriptionBugfixErrata.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_BUGFIX_ERRATA), ERRATA_FOLDER_NAME + "bugfixEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_BUGFIX_ERRATA, true), ERRATA_FOLDER_NAME + "beta/bugfixEmailBody.html"),
        entry(new TemplateDefinition(GOOGLE_CHAT, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_BUGFIX_ERRATA), ERRATA_FOLDER_NAME + "newSubscriptionBugfixErrata.json"),
        entry(new TemplateDefinition(MS_TEAMS, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_BUGFIX_ERRATA), ERRATA_FOLDER_NAME + "newSubscriptionBugfixErrata.json"),
        entry(new TemplateDefinition(SLACK, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_BUGFIX_ERRATA), ERRATA_FOLDER_NAME + "newSubscriptionBugfixErrata.json"),
        entry(new TemplateDefinition(SLACK, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_BUGFIX_ERRATA, true), ERRATA_FOLDER_NAME + "beta/newSubscriptionBugfixErrata.json"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_SECURITY_ERRATA), ERRATA_FOLDER_NAME + "newSubscriptionSecurityErrata.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_SECURITY_ERRATA), ERRATA_FOLDER_NAME + "securityEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_SECURITY_ERRATA, true), ERRATA_FOLDER_NAME + "beta/securityEmailBody.html"),
        entry(new TemplateDefinition(GOOGLE_CHAT, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_SECURITY_ERRATA), ERRATA_FOLDER_NAME + "newSubscriptionSecurityErrata.json"),
        entry(new TemplateDefinition(MS_TEAMS, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_SECURITY_ERRATA), ERRATA_FOLDER_NAME + "newSubscriptionSecurityErrata.json"),
        entry(new TemplateDefinition(SLACK, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_SECURITY_ERRATA), ERRATA_FOLDER_NAME + "newSubscriptionSecurityErrata.json"),
        entry(new TemplateDefinition(SLACK, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_SECURITY_ERRATA, true), ERRATA_FOLDER_NAME + "beta/newSubscriptionSecurityErrata.json"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_ENHANCEMENT_ERRATA), ERRATA_FOLDER_NAME + "newSubscriptionEnhancementErrata.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_ENHANCEMENT_ERRATA), ERRATA_FOLDER_NAME + "enhancementEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_ENHANCEMENT_ERRATA, true), ERRATA_FOLDER_NAME + "beta/enhancementEmailBody.html"),
        entry(new TemplateDefinition(GOOGLE_CHAT, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_ENHANCEMENT_ERRATA), ERRATA_FOLDER_NAME + "newSubscriptionEnhancementErrata.json"),
        entry(new TemplateDefinition(MS_TEAMS, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_ENHANCEMENT_ERRATA), ERRATA_FOLDER_NAME + "newSubscriptionEnhancementErrata.json"),
        entry(new TemplateDefinition(SLACK, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_ENHANCEMENT_ERRATA), ERRATA_FOLDER_NAME + "newSubscriptionEnhancementErrata.json"),
        entry(new TemplateDefinition(SLACK, BUNDLE_NAME, ERRATA_APP_NAME, ERRATA_NEW_SUBSCRIPTION_ENHANCEMENT_ERRATA, true), ERRATA_FOLDER_NAME + "beta/newSubscriptionEnhancementErrata.json"),

        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, ERRATA_APP_NAME, null), ERRATA_FOLDER_NAME + "dailyEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, ERRATA_APP_NAME, null, true), ERRATA_FOLDER_NAME + "beta/dailyEmailBody.html"),

        // Subscriptions Usage
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, SUBSCRIPTIONS_USAGE_APP_NAME, SUBSCRIPTIONS_USAGE_EXCEEDED_UTILIZATION_THRESHOLD), SUBSCRIPTIONS_USAGE_FOLDER_NAME + "usageThresholdExceededEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, SUBSCRIPTIONS_USAGE_APP_NAME, SUBSCRIPTIONS_USAGE_EXCEEDED_UTILIZATION_THRESHOLD), SUBSCRIPTIONS_USAGE_FOLDER_NAME + "usageThresholdExceededEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, SUBSCRIPTIONS_USAGE_APP_NAME, SUBSCRIPTIONS_USAGE_EXCEEDED_UTILIZATION_THRESHOLD, true), SUBSCRIPTIONS_USAGE_FOLDER_NAME + "beta/usageThresholdExceededEmailBody.html")
    );
}
