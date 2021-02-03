package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscription.EmailSubscriptionType;
import io.quarkus.qute.TemplateInstance;

public abstract class EmailTemplate {

    public abstract TemplateInstance getTitle(String eventType, EmailSubscriptionType type);

    public abstract TemplateInstance getBody(String eventType, EmailSubscriptionType type);

    public abstract boolean isSupported(String eventType, EmailSubscriptionType type);
}
