package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

// Name needs to be "Compliance" to read templates from resources/templates/Compliance
public class Compliance implements EmailTemplate {

    private static final String COMPLIANCE_BELOW_THRESHOLD = "compliance-below-threshold";
    private static final String REPORT_UPLOAD_FAILED = "report-upload-failed";

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(COMPLIANCE_BELOW_THRESHOLD)) {
                return Templates.complianceBelowThresholdEmailTitle();
            } else if (eventType.equals(REPORT_UPLOAD_FAILED)) {
                return Templates.reportUploadFailedEmailTitle();
            }
        }

        return Templates.dailyEmailTitle();
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(COMPLIANCE_BELOW_THRESHOLD)) {
                return Templates.complianceBelowThresholdEmailBody();
            } else if (eventType.equals(REPORT_UPLOAD_FAILED)) {
                return Templates.reportUploadFailedEmailBody();
            }
        }

        return Templates.dailyEmailBody();
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return true;
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return true;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance complianceBelowThresholdEmailTitle();

        public static native TemplateInstance reportUploadFailedEmailTitle();

        public static native TemplateInstance complianceBelowThresholdEmailBody();

        public static native TemplateInstance reportUploadFailedEmailBody();

        public static native TemplateInstance dailyEmailTitle();

        public static native TemplateInstance dailyEmailBody();
    }

}
