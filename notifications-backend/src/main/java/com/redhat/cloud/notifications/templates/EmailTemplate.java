package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.TemplateInstance;

/*
We currently have the email templates hardcoded, but ideally they all should be in the database to make it easier for
applications to onboard without having to update any code.
 */

public interface EmailTemplate {

    TemplateInstance getTitle(String eventType, EmailSubscriptionType type);

    TemplateInstance getBody(String eventType, EmailSubscriptionType type);

    // This method is used to know if a specific combination of event and subscription are supported
    // if isSupported returns true, getTitle and getBody should return a TemplateInstance for that particular combination
    boolean isSupported(String eventType, EmailSubscriptionType type);

    // This is method is used to determine if we should show the subscription in the user-preferences.
    // If this method returns true, at least one combination of any event and the subscription should return true
    boolean isEmailSubscriptionSupported(EmailSubscriptionType type);
}
