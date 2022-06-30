package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

// Name needs to be "Source" to read templates from resources/templates/Source
public class Sources implements EmailTemplate {

    private static final String AVAILABILITY_STATUS = "availability-status";

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(AVAILABILITY_STATUS)) {
                return Templates.availabilityStatusEmailTitle();
            }
        }

        throw new UnsupportedOperationException(String.format(
                "No email title template for Source event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(AVAILABILITY_STATUS)) {
                return Templates.availabilityStatusEmailBody();
            }
        }

        throw new UnsupportedOperationException(String.format(
                "No email body template for Source event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return eventType.equals(AVAILABILITY_STATUS) && type == EmailSubscriptionType.INSTANT;
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance availabilityStatusEmailTitle();

        public static native TemplateInstance availabilityStatusEmailBody();
    }

}
