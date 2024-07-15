package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.Event;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EmailPendoResolver {

    public static String GENERAL_PENDO_MESSAGE = "You can also receive notifications on tools like Slack, Microsoft Teams, Google chat, and more! Go to <b><a href=\"%s/settings/integrations\" target=\"_blank\">integrations</a></b> to set those up.";
    public static String GENERAL_PENDO_TITLE = "Did you know?";

    @Inject
    EngineConfig engineConfig;

    @Inject
    Environment environment;

    public EmailPendo getPendoEmailMessage(final Event event, boolean forcedEmail) {
        if (!isPendoMessageEnabled(event, forcedEmail)) {
            return null;
        } else {
            String pendoMessage = String.format(GENERAL_PENDO_MESSAGE, environment.url());
            String pendoTitle = GENERAL_PENDO_TITLE;
            return new EmailPendo(pendoTitle, pendoMessage);
        }
    }

    private boolean isPendoMessageEnabled(final Event event, boolean forcedEmail) {
        // pendo message must not be shown on OCM and environment with emails only mode
        return !EmailActorsResolver.isOCMApp(event) && !engineConfig.isEmailsOnlyModeEnabled() && !forcedEmail;
    }
}
