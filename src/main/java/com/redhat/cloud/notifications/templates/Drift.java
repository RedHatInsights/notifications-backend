package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

// Name needs to be "Drift" to read templates from resources/templates/Drift
public class Drift extends AbstractEmailTemplate {

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            return Templates.newBaselineDriftInstantEmailTitle();
        }

        throw new UnsupportedOperationException(String.format(
                "No email title template for Drift event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            return Templates.newBaselineDriftInstantEmailBody();
        }

        throw new UnsupportedOperationException(String.format(
                "No email body template for Drift event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return eventType.equals("drift-baseline-detected") && type == EmailSubscriptionType.INSTANT;
    }

    @Override
    public boolean isSupported(EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT;
    }

    @CheckedTemplate
    public static class Templates {

        public static native TemplateInstance newBaselineDriftInstantEmailTitle();

        public static native TemplateInstance newBaselineDriftInstantEmailBody();

    }

}
