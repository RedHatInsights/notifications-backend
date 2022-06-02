package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

public class Patch implements EmailTemplate {

    private static final String NewAdvisory = "new-advisory";

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(Patch.NewAdvisory)) {
                return Templates.newAdvisoriesInstantEmailTitle();
            }
        }

        return Templates.dailyEmailTitle();
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(Patch.NewAdvisory)) {
                return Templates.newAdvisoriesInstantEmailBody();
            }
        }

        return Templates.dailyEmailBody();
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return (type == EmailSubscriptionType.INSTANT &&
                (eventType.equals(Patch.NewAdvisory)));
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance newAdvisoriesInstantEmailTitle();

        public static native TemplateInstance newAdvisoriesInstantEmailBody();

        public static native TemplateInstance dailyEmailTitle();

        public static native TemplateInstance dailyEmailBody();
    }
}
