package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

// Name needs to be "Advisor" to read templates from resources/templates/Advisor
public class Advisor implements EmailTemplate {

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals("new-recommendation")) {
                return Templates.newRecommendationInstantEmailTitle();
            } else if (eventType.equals("resolved-recommendation")) {
                return Templates.resolvedRecommendationInstantEmailTitle();
            } else if (eventType.equals("deactivated-recommendation")) {
                return Templates.deactivatedRecommendationInstantEmailTitle();
        } else if (type == EmailSubscriptionType.DAILY) {
            return Templates.dailyEmailTitle();
        }

        throw new UnsupportedOperationException(String.format(
                "No email title template for Advisor event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals("new-recommendation")) {
                return Templates.newRecommendationInstantEmailBody();
            } else if (eventType.equals("resolved-recommendation")) {
                return Templates.resolvedRecommendationInstantEmailBody();
            } else if (eventType.equals("deactivated-recommendation")) {
                return Templates.deactivatedRecommendationInstantEmailBody();
            }
        } else if (type == EmailSubscriptionType.DAILY) {
            return Templates.dailyEmailBody();
        }

        throw new UnsupportedOperationException(String.format(
                "No email body template for Advisor event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return (
            type == EmailSubscriptionType.DAILY ||
            type == EmailSubscriptionType.INSTANT && (
                eventType.equals("new-recommendation") ||
                eventType.equals("resolved-recommendation") ||
                eventType.equals("deactivated-recommendation")
            )
        );
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT || type == EmailSubscriptionType.DAILY;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance newRecommendationInstantEmailTitle();

        public static native TemplateInstance newRecommendationInstantEmailBody();

        public static native TemplateInstance resolvedRecommendationInstantEmailTitle();

        public static native TemplateInstance resolvedRecommendationInstantEmailBody();

        public static native TemplateInstance deactivatedRecommendationInstantEmailTitle();

        public static native TemplateInstance deactivatedRecommendationInstantEmailBody();

        public static native TemplateInstance dailyEmailTitle();

        public static native TemplateInstance dailyEmailBody();
    }

}
