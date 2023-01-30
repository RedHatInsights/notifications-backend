package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

// Name needs to be "Source" to read templates from resources/templates/Source
@ApplicationScoped
public class Sources implements EmailTemplate {

    protected static final String AVAILABILITY_STATUS = "availability-status";

    @Inject
    FeatureFlipper featureFlipper;

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(AVAILABILITY_STATUS)) {
                return getAvailabilityStatusEmailTitle();
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
                return getAvailabilityStatusEmailBody();
            }
        }

        throw new UnsupportedOperationException(String.format(
                "No email body template for Source event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    private TemplateInstance getAvailabilityStatusEmailBody() {
        if (featureFlipper.isSourcesEmailTemplatesV2Enabled()) {
            return Templates.availabilityStatusEmailBodyV2();
        }
        return Templates.availabilityStatusEmailBody();
    }

    private TemplateInstance getAvailabilityStatusEmailTitle() {
        if (featureFlipper.isSourcesEmailTemplatesV2Enabled()) {
            return Templates.availabilityStatusEmailTitleV2();
        }
        return Templates.availabilityStatusEmailTitle();
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

        public static native TemplateInstance availabilityStatusEmailTitleV2();

        public static native TemplateInstance availabilityStatusEmailBodyV2();
    }

}
