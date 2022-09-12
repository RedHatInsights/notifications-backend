package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

import static com.redhat.cloud.notifications.events.FromCamelHistoryFiller.INTEGRATION_FAILED_EVENT_TYPE;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.INTEGRATION_DISABLED_EVENT_TYPE;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;

// Name needs to be "ConsoleNotifications" to read templates from resources/templates/ConsoleNotifications
public class ConsoleNotifications implements EmailTemplate {

    private static final String NO_TITLE_FOUND_MSG = "No email title template found for ConsoleNotifications event_type: %s";
    private static final String NO_BODY_FOUND_MSG = "No email body template found for ConsoleNotifications event_type: %s";

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == INSTANT) {
            switch (eventType) {
                case INTEGRATION_FAILED_EVENT_TYPE:
                    return Templates.failedIntegrationTitle();
                case INTEGRATION_DISABLED_EVENT_TYPE:
                    return Templates.integrationDisabledTitle();
                default:
                    // Do nothing.
                    break;
            }
        }
        throw new UnsupportedOperationException(String.format(NO_TITLE_FOUND_MSG, eventType));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == INSTANT) {
            switch (eventType) {
                case INTEGRATION_FAILED_EVENT_TYPE:
                    return Templates.failedIntegrationBody();
                case INTEGRATION_DISABLED_EVENT_TYPE:
                    return Templates.integrationDisabledBody();
                default:
                    // Do nothing.
                    break;
            }
        }
        throw new UnsupportedOperationException(String.format(NO_BODY_FOUND_MSG, eventType));
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return type.equals(INSTANT);
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return false;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance failedIntegrationTitle();

        public static native TemplateInstance failedIntegrationBody();

        public static native TemplateInstance integrationDisabledTitle();

        public static native TemplateInstance integrationDisabledBody();
    }
}
