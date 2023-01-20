package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

// Name needs to be "Compliance" to read templates from resources/templates/Compliance
@ApplicationScoped
public class Compliance implements EmailTemplate {

    protected static final String COMPLIANCE_BELOW_THRESHOLD = "compliance-below-threshold";
    protected static final String REPORT_UPLOAD_FAILED = "report-upload-failed";

    @Inject
    FeatureFlipper featureFlipper;

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(COMPLIANCE_BELOW_THRESHOLD)) {
                return getComplianceBelowThresholdEmailTitle();
            } else if (eventType.equals(REPORT_UPLOAD_FAILED)) {
                return getReportUploadFailedEmailTitle();
            }
        }

        return getDailyEmailTitle();
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(COMPLIANCE_BELOW_THRESHOLD)) {
                return getComplianceBelowThresholdEmailBody();
            } else if (eventType.equals(REPORT_UPLOAD_FAILED)) {
                return getReportUploadFailedEmailBody();
            }
        }

        return getDailyEmailBody();
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return true;
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return true;
    }

    private TemplateInstance getComplianceBelowThresholdEmailTitle() {
        if (featureFlipper.isComplianceEmailTemplatesV2Enabled()) {
            return Compliance.Templates.complianceBelowThresholdEmailTitleV2();
        }
        return Compliance.Templates.complianceBelowThresholdEmailTitle();
    }

    private TemplateInstance getReportUploadFailedEmailTitle() {
        if (featureFlipper.isComplianceEmailTemplatesV2Enabled()) {
            return Compliance.Templates.reportUploadFailedEmailTitleV2();
        }
        return Compliance.Templates.reportUploadFailedEmailTitle();
    }

    private TemplateInstance getDailyEmailTitle() {
        if (featureFlipper.isComplianceEmailTemplatesV2Enabled()) {
            return Compliance.Templates.dailyEmailTitleV2();
        }
        return Compliance.Templates.dailyEmailTitle();
    }

    private TemplateInstance getComplianceBelowThresholdEmailBody() {
        if (featureFlipper.isComplianceEmailTemplatesV2Enabled()) {
            return Compliance.Templates.complianceBelowThresholdEmailBodyV2();
        }
        return Compliance.Templates.complianceBelowThresholdEmailBody();
    }

    private TemplateInstance getDailyEmailBody() {
        if (featureFlipper.isComplianceEmailTemplatesV2Enabled()) {
            return Compliance.Templates.dailyEmailBodyV2();
        }
        return Compliance.Templates.dailyEmailBody();
    }

    private TemplateInstance getReportUploadFailedEmailBody() {
        if (featureFlipper.isComplianceEmailTemplatesV2Enabled()) {
            return Compliance.Templates.reportUploadFailedEmailBodyV2();
        }
        return Compliance.Templates.reportUploadFailedEmailBody();
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance complianceBelowThresholdEmailTitle();

        public static native TemplateInstance reportUploadFailedEmailTitle();

        public static native TemplateInstance complianceBelowThresholdEmailBody();

        public static native TemplateInstance reportUploadFailedEmailBody();

        public static native TemplateInstance dailyEmailTitle();

        public static native TemplateInstance dailyEmailBody();

        public static native TemplateInstance complianceBelowThresholdEmailTitleV2();

        public static native TemplateInstance reportUploadFailedEmailTitleV2();

        public static native TemplateInstance dailyEmailTitleV2();

        public static native TemplateInstance complianceBelowThresholdEmailBodyV2();

        public static native TemplateInstance reportUploadFailedEmailBodyV2();

        public static native TemplateInstance dailyEmailBodyV2();

    }

}
