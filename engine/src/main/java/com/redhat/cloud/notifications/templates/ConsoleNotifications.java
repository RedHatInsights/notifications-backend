package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

// Class name is the folder name in resources/templates/
public class ConsoleNotifications implements EmailTemplate {
    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        return Templates.failedIntegrationTitle();
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        return Templates.failedIntegrationBody();
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return type.equals(EmailSubscriptionType.INSTANT);
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return false;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance failedIntegrationTitle();

        public static native TemplateInstance failedIntegrationBody();

    }
}
