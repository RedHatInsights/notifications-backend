package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

public class Inventory implements EmailTemplate {
  
    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            return Templates.validationErrorEmailTitle();

        }

        return Templates.dailyEmailTitle();
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            return Templates.validationErrorEmailBody();
        }

        return Templates.dailyEmailBody();
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return true;
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return true;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance validationErrorEmailTitle();

        public static native TemplateInstance validationErrorEmailBody();

        public static native TemplateInstance dailyEmailTitle();

        public static native TemplateInstance dailyEmailBody();
    }

}
