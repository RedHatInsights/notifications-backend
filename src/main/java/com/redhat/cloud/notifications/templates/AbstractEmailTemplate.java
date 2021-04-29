package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.TemplateInstance;

public abstract class AbstractEmailTemplate {

    public abstract TemplateInstance getTitle(String eventType, EmailSubscriptionType type);

    public abstract TemplateInstance getBody(String eventType, EmailSubscriptionType type);

    public abstract boolean isSupported(String eventType, EmailSubscriptionType type);

    public abstract boolean isSupported(EmailSubscriptionType type);
}
