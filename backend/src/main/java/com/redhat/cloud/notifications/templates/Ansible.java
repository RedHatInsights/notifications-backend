package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

// Name needs to be "Ansible" to read templates from resources/templates/Ansible
public class Ansible implements EmailTemplate {

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals("new-recommendation")) {
                return Templates.newRecommendationInstantEmailTitle();
            } else if (eventType.equals("resolved-recommendation")) {
                return Templates.resolvedRecommendationInstantEmailTitle();
            }
        }

        throw new UnsupportedOperationException(String.format(
                "No email title template for Ansible event_type: %s and EmailSubscription: %s found.",
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
            }
        }

        throw new UnsupportedOperationException(String.format(
                "No email body template for Ansible event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return (eventType.equals("new-recommendation") || eventType.equals("resolved-recommendation")) && type == EmailSubscriptionType.INSTANT;
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance newRecommendationInstantEmailTitle();

        public static native TemplateInstance newRecommendationInstantEmailBody();

        public static native TemplateInstance resolvedRecommendationInstantEmailTitle();

        public static native TemplateInstance resolvedRecommendationInstantEmailBody();
    }

}
