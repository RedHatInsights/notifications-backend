package com.redhat.cloud.notifications.qute.templates.i18n;

import io.quarkus.qute.i18n.Message;
import io.quarkus.qute.i18n.MessageBundle;

@MessageBundle("layout")
public interface EmailLayoutMessages {

    @Message("Go to")
    String goToLabel();

    @Message("This email was sent by Red Hat Hybrid Cloud Console, it is critical or requires action.")
    String emailSentByCritical();

    @Message("Therefore, you are receiving this message regardless of your email preferences.")
    String emailSentByCriticalDisclaimer();

    @Message("This email was sent by Red Hat Hybrid Cloud Console")
    String emailSentByPrefix();

    @Message("Manage email preferences")
    String manageEmailPreferences();
}
