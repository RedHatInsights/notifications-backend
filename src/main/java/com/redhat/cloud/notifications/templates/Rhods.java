package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

// Name needs to be "Rhods" to read templates from resources/templates/Rhods
public class Rhods implements EmailTemplate {

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {

        return Templates.instantEmailTitle();
        
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {

        return Templates.instantEmailBody();
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return true;
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return true;
    }

    @CheckedTemplate
    public static class Templates {

        public static native TemplateInstance instantEmailTitle();

        public static native TemplateInstance instantEmailBody();

    }

}
