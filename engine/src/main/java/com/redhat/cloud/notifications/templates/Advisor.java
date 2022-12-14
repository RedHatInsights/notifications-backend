package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.DEACTIVATED_RECOMMENDATION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.NEW_RECOMMENDATION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.RESOLVED_RECOMMENDATION;

// Name needs to be "Advisor" to read templates from resources/templates/Advisor
public class Advisor implements EmailTemplate {

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(NEW_RECOMMENDATION)) {
                return Templates.newRecommendationInstantEmailTitle();
            } else if (eventType.equals(RESOLVED_RECOMMENDATION)) {
                return Templates.resolvedRecommendationInstantEmailTitle();
            } else if (eventType.equals(DEACTIVATED_RECOMMENDATION)) {
                return Templates.deactivatedRecommendationInstantEmailTitle();
            }
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
            if (eventType.equals(NEW_RECOMMENDATION)) {
                return Templates.newRecommendationInstantEmailBody();
            } else if (eventType.equals(RESOLVED_RECOMMENDATION)) {
                return Templates.resolvedRecommendationInstantEmailBody();
            } else if (eventType.equals(DEACTIVATED_RECOMMENDATION)) {
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
                eventType.equals(NEW_RECOMMENDATION) ||
                eventType.equals(RESOLVED_RECOMMENDATION) ||
                eventType.equals(DEACTIVATED_RECOMMENDATION)
            ));
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
