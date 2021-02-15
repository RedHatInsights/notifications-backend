package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscription.EmailSubscriptionType;
import io.quarkus.qute.TemplateInstance;

public class EmailTemplateFactory {
    private EmailTemplateFactory() {

    }

    public static EmailTemplate get(String bundle, String application) {
        if (bundle.toLowerCase().equals("insights") && application.toLowerCase().equals("policies")) {
            return new Policies();
        }

        return new EmailTemplateNotSupported();
    }
}

class EmailTemplateNotSupported extends EmailTemplate {
    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return false;
    }
}
