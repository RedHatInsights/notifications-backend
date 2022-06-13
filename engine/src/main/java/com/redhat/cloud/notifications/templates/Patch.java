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
        } else if (type == EmailSubscriptionType.DAILY) {
            return Templates.dailyEmailTitle();
        }

        throw new UnsupportedOperationException(String.format(
        "No email title template for Patch event_type: %s and EmailSubscription: %s found.",
        eventType, type));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(Patch.NewAdvisory)) {
                return Templates.newAdvisoriesInstantEmailBody();
            }
        } else if (type == EmailSubscriptionType.DAILY) {
            return Templates.dailyEmailBody();
        }

        throw new UnsupportedOperationException(String.format(
        "No email body template for Patch event_type: %s and EmailSubscription: %s found.",
        eventType, type));
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return (type == EmailSubscriptionType.INSTANT &&
                (eventType.equals(Patch.NewAdvisory))) ||
                type == EmailSubscriptionType.DAILY;
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT || type == EmailSubscriptionType.DAILY;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance newAdvisoriesInstantEmailTitle();

        public static native TemplateInstance newAdvisoriesInstantEmailBody();

        public static native TemplateInstance dailyEmailTitle();

        public static native TemplateInstance dailyEmailBody();
    }
}
