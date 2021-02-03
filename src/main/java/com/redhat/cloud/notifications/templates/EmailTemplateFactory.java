package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscription.EmailSubscriptionType;
import io.quarkus.qute.TemplateInstance;

public class EmailTemplateFactory {
    private EmailTemplateFactory() {

    }

    public static EmailTemplate get(String application) {
        switch (application.toLowerCase()) {
            case "policies":
                return new Policies();
            default:
                return new EmailTemplateNotSupported();
        }
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
