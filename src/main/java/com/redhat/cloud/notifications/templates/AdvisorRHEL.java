package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

// Name needs to be "AdvisorRHEl" to read templates from resources/templates/AdvisorOpenshift
public class AdvisorRHEL implements EmailTemplate {

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals("resolved-recommendation")) {
                return Templates.newRecommendationInstantEmailTitle();
            } else if (eventType.equals("weekly-digest")) {
                return Templates.weeklyDigestEmailTitle();
            }
        }

        throw new UnsupportedOperationException(String.format(
                "No email title template for RHEL Advisor event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals("resolved-recommendation")) {
                return Templates.newRecommendationInstantEmailBody();
            } else if (eventType.equals("weekly-digest")) {
                return Templates.weeklyDigestEmailBody();
            }
        }

        throw new UnsupportedOperationException(String.format(
                "No email body template for RHEL Advisor event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return (eventType.equals("resolved-recommendation") || eventType.equals("weekly-digest")) && type == EmailSubscriptionType.INSTANT;
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT;
    }

    @CheckedTemplate
    public static class Templates {

        public static native TemplateInstance newRecommendationInstantEmailTitle();

        public static native TemplateInstance newRecommendationInstantEmailBody();

        public static native TemplateInstance weeklyDigestEmailTitle();

        public static native TemplateInstance weeklyDigestEmailBody();

    }

}
