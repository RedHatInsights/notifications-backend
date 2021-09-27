package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

import java.util.Arrays;
import java.util.List;

// Name needs to be "Rhosak" to read templates from resources/templates/Rhosak
public class Rhosak implements EmailTemplate {
    private static final String SCHEDULED_UPGRADE = "scheduled-upgrade";
    private static final String DISRUPTION = "disruption";
    private static final String INSTANCE_CREATED = "instance-created";
    private static final String INSTANCE_DELETED = "instance-deleted";
    private static final String ACTION_REQUIRED = "action-required";
    private static final List<String> ALLOWED_EVENT_TYPES = Arrays.asList(SCHEDULED_UPGRADE, DISRUPTION, INSTANCE_DELETED, INSTANCE_CREATED, ACTION_REQUIRED);

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.DAILY) {
            return Templates.dailyRhosakEmailsTitle();
        }
        if (eventType.equals(DISRUPTION)) {
            return Templates.serviceDisruptionTitle();
        }
        if (eventType.equals(INSTANCE_CREATED)) {
            return Templates.instanceCreatedTitle();
        }
        if (eventType.equals(INSTANCE_DELETED)) {
            return Templates.instanceDeletedTitle();
        }
        if (eventType.equals(ACTION_REQUIRED)) {
            return Templates.actionRequiredTitle();
        }
        if (eventType.equals(SCHEDULED_UPGRADE)) {
            return Templates.scheduledUpgradeTitle();
        }
        throw new UnsupportedOperationException(String.format(
                "No email title template for RHOSAK event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.DAILY) {
            return Templates.dailyRhosakEmailsBody();
        }
        if (eventType.equals(DISRUPTION)) {
            return Templates.serviceDisruptionBody();
        }
        if (eventType.equals(INSTANCE_CREATED)) {
            return Templates.instanceCreatedBody();
        }
        if (eventType.equals(INSTANCE_DELETED)) {
            return Templates.instanceDeletedBody();
        }
        if (eventType.equals(ACTION_REQUIRED)) {
            return Templates.actionRequiredBody();
        }
        if (eventType.equals(SCHEDULED_UPGRADE)) {
            return Templates.scheduledUpgradeBody();
        }

        throw new UnsupportedOperationException(String.format(
                "No email body template for RHOSAK event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return ALLOWED_EVENT_TYPES.contains(eventType);
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return true;
    }

    @CheckedTemplate
    public static class Templates {

        public static native TemplateInstance actionRequiredTitle();

        public static native TemplateInstance actionRequiredBody();

        public static native TemplateInstance instanceCreatedTitle();

        public static native TemplateInstance instanceCreatedBody();

        public static native TemplateInstance instanceDeletedTitle();

        public static native TemplateInstance instanceDeletedBody();

        public static native TemplateInstance dailyRhosakEmailsTitle();

        public static native TemplateInstance dailyRhosakEmailsBody();

        public static native TemplateInstance serviceDisruptionTitle();

        public static native TemplateInstance serviceDisruptionBody();

        public static native TemplateInstance scheduledUpgradeTitle();

        public static native TemplateInstance scheduledUpgradeBody();
    }
}
