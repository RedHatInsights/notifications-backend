package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.TemplateInstance;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EmailTemplateFactory {

    private static final String RHOSAK = "rhosak";
    private static final String APPLICATION_SERVICES = "application-services";
    private static final String RHEL = "rhel";
    private static final String POLICIES = "policies";
    private static final String ADVISOR = "advisor";
    private static final String COMPLIANCE = "compliance";
    private static final String DRIFT = "drift";
    private static final String OPENSHIFT = "openshift";

    private static final String BUNDLE_ANSIBLE = "ansible";
    private static final String APP_ANSIBLE_REPORTS = "reports";

    public EmailTemplate get(String bundle, String application) {
        if (bundle.equalsIgnoreCase(RHEL)) {
            switch (application.toLowerCase()) {
                case POLICIES:
                    return new Policies();
                case ADVISOR:
                    return new Advisor();
                case COMPLIANCE:
                    return new Compliance();
                case DRIFT:
                    return new Drift();
                default:
                    break;
            }
        } else if (bundle.equalsIgnoreCase(OPENSHIFT)) {
            if (application.equalsIgnoreCase(ADVISOR)) {
                return new AdvisorOpenshift();
            }
        } else if (bundle.equalsIgnoreCase(APPLICATION_SERVICES)) {
            if (application.equalsIgnoreCase(RHOSAK)) {
                return new Rhosak();
            }
        } else if (bundle.equalsIgnoreCase(BUNDLE_ANSIBLE)) {
            if (application.equalsIgnoreCase(APP_ANSIBLE_REPORTS)) {
                return new Ansible();
            }
        }
        return new EmailTemplateNotSupported();
    }
}

class EmailTemplateNotSupported implements EmailTemplate {
    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return false;
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return false;
    }
}
