package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

// Name needs to be "AdvisorOpenshift" to read templates from resources/templates/AdvisorOpenshift
public class AdvisorOpenshift implements EmailTemplate {

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals("new-recommendation")) {
                return Templates.newRecommendationInstantEmailTitle();
            } else if (eventType.equals("weekly-digest")) {
                return Templates.weeklyDigestEmailTitle();
            }
        }

        throw new UnsupportedOperationException(String.format(
                "No email title template for OpenShift Advisor event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals("new-recommendation")) {
                return Templates.newRecommendationInstantEmailBody();
            } else if (eventType.equals("weekly-digest")) {
                return Templates.weeklyDigestEmailBody();
            }
        }

        throw new UnsupportedOperationException(String.format(
                "No email body template for OpenShift Advisor event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return (eventType.equals("new-recommendation") || eventType.equals("weekly-digest")) && type == EmailSubscriptionType.INSTANT;
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
