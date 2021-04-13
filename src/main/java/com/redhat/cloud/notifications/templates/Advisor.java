package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

// Name needs to be "Advisor" to read templates from resources/templates/Advisor
public class Advisor extends AbstractEmailTemplate {

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            return Templates.newRecommendationInstantEmailTitle();
        }

        return null;
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            return Templates.newRecommendationInstantEmailBody();
        }

        return null;
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
