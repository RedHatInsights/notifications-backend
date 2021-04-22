package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

// Name needs to be "AdvisorOpenshift" to read templates from resources/templates/AdvisorOpenshift
public class AdvisorOpenshift extends AbstractEmailTemplate {

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            return Templates.newRecommendationInstantEmailTitle();
        }

        throw new UnsupportedOperationException(String.format(
                "No email title template for Advisor event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            return Templates.newRecommendationInstantEmailBody();
        }

        throw new UnsupportedOperationException(String.format(
                "No email body template for Advisor event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return eventType.equals("new-recommendation") && type == EmailSubscriptionType.INSTANT;
    }

    @CheckedTemplate
    public static class Templates {

        public static native TemplateInstance newRecommendationInstantEmailTitle();

        public static native TemplateInstance newRecommendationInstantEmailBody();

    }

}
