package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

/**
 * Name needs to be "Default" to read templates from resources/templates/Default
 *
 * This class handles the default emails by wrapping the original EmailTemplate.
 * If the title or body is already supported, it will pass trough.
 * If the title or body is not supported in the wrapped class AND the type is instant, it will return the default one.
 * Else it will throw an exception (or reuse the one provided by the call to the wrapped class)
 * This is only useful for the templates as files flow.
 *
 * For templates in database, it provides static methods for getting the TemplateInstance for the title and body.
 */
public class Default implements EmailTemplate {
    // After migration, we can remove this file

    final EmailTemplate wrappedEmailTemplate;

    public Default(EmailTemplate wrappedEmailTemplate) {
        this.wrappedEmailTemplate = wrappedEmailTemplate;
    }

    public static TemplateInstance getTitle() {
        return Templates.instantEmailTitle();
    }

    public static TemplateInstance getBody(boolean hasTitle, boolean hasBody) {
        String missingTemplateWarning = null;

        if (hasTitle != hasBody) {
            missingTemplateWarning = String.format(
                    "The %s template was found but not the %s template.",
                    hasTitle ? "title" : "body",
                    hasTitle ? "body" : "title"
            );
        }

        return Templates.instantEmailBody()
                        .data("mixed_usage_missing_warning", missingTemplateWarning);
    }

    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (wrappedEmailTemplate.isSupported(eventType, type)) {
            return this.wrappedEmailTemplate.getTitle(eventType, type);
        }

        return null;
    }

    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (wrappedEmailTemplate.isSupported(eventType, type)) {
            return this.wrappedEmailTemplate.getBody(eventType, type);
        }

        return null;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    private static class Templates {

        public static native TemplateInstance instantEmailTitle();

        public static native TemplateInstance instantEmailBody();
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT || wrappedEmailTemplate.isSupported(eventType, type);
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT || wrappedEmailTemplate.isEmailSubscriptionSupported(type);
    }
}
