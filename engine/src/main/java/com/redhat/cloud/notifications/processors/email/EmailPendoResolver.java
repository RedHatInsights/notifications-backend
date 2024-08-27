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

    public static final String OUTAGE_PENDO_MESSAGE = "Between Aug 22, 07:19 AM UTC and Aug 26, 12:47 PM UTC, the notifications email service experienced an outage. This email was delayed from that time period.";
    public static final String OUTAGE_PENDO_TITLE = "Outage notice";

    @Inject
    EngineConfig engineConfig;

    @Inject
    Environment environment;

    public EmailPendo getPendoEmailMessage(final Event event, boolean forcedEmail, boolean incidentMessage) {
        if (incidentMessage) {
            String pendoMessage = OUTAGE_PENDO_MESSAGE;
            String pendoTitle = OUTAGE_PENDO_TITLE;
            return new EmailPendo(pendoTitle, pendoMessage);
        } else if (!isPendoMessageEnabled(event, forcedEmail)) {
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
