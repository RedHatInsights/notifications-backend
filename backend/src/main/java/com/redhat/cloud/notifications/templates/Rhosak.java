package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

// Name needs to be "Rhosak" to read templates from resources/templates/Rhosak
public class Rhosak implements EmailTemplate {
    private static final String SCHEDULED_UPGRADE = "scheduled-upgrade";
    private static final String DISRUPTION = "disruption";
    private static final String INSTANCE_CREATED = "instance-created";
    private static final String INSTANCE_DELETED = "instance-deleted";
    private static final String ACTION_REQUIRED = "action-required";

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (eventType.equals(SCHEDULED_UPGRADE)) {
            return Templates.scheduledUpgradeTitle();
        } else if (eventType.equals(DISRUPTION)) {
            return Templates.serviceDisruptionTitle();
        } else if (eventType.equals(INSTANCE_CREATED)) {
            return Templates.instanceCreatedTitle();
        } else if (eventType.equals(INSTANCE_DELETED)) {
            return Templates.instanceDeletedTitle();
        } else if (eventType.equals(ACTION_REQUIRED)) {
            return Templates.actionRequiredTitle();
        }

        throw new UnsupportedOperationException(String.format(
                "No email title template for RHOSAK event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (eventType.equals(SCHEDULED_UPGRADE)) {
            return Templates.scheduledUpgradeBody();
        } else if (eventType.equals(DISRUPTION)) {
            return Templates.serviceDisruptionBody();
        } else if (eventType.equals(INSTANCE_CREATED)) {
            return Templates.instanceCreatedBody();
        } else if (eventType.equals(INSTANCE_DELETED)) {
            return Templates.instanceDeletedBody();
        } else if (eventType.equals(ACTION_REQUIRED)) {
            return Templates.actionRequiredBody();
        }

        throw new UnsupportedOperationException(String.format(
                "No email body template for RHOSAK event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        if (eventType.equals(SCHEDULED_UPGRADE)) {
            return type == EmailSubscriptionType.DAILY; // scheduled upgrades emails are sent on daily basis
        }

        return true;
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

        public static native TemplateInstance scheduledUpgradeTitle();

        public static native TemplateInstance scheduledUpgradeBody();

        public static native TemplateInstance serviceDisruptionTitle();

        public static native TemplateInstance serviceDisruptionBody();
    }
}
