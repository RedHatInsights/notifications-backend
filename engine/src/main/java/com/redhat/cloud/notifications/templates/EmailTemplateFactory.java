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
    private static final String COST_MANAGEMENT = "cost-management";
    private static final String DRIFT = "drift";
    private static final String OPENSHIFT = "openshift";
    private static final String CONSOLE = "console";
    private static final String NOTIFICATIONS = "notifications";
    private static final String RBAC = "rbac";
    private static final String SOURCES = "sources";
    private static final String VULNERABILITY = "vulnerability";
    private static final String EDGE_MANAGEMENT = "edge-management";
    private static final String PATCH = "patch";
    private static final String MALWARE_DETECTION = "malware-detection";

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
                case VULNERABILITY:
                    return new Vulnerability();
                case EDGE_MANAGEMENT:
                    return new EdgeManagement();
                case PATCH:
                    return new Patch();
                case MALWARE_DETECTION:
                    return new MalwareDetection();
                default:
                    break;
            }
        } else if (bundle.equalsIgnoreCase(OPENSHIFT)) {
            if (application.equalsIgnoreCase(ADVISOR)) {
                return new AdvisorOpenshift();
            } else if (application.equalsIgnoreCase(COST_MANAGEMENT)) {
                return new CostManagement();
            }
        } else if (bundle.equalsIgnoreCase(APPLICATION_SERVICES)) {
            if (application.equalsIgnoreCase(RHOSAK)) {
                return new Rhosak();
            }
        } else if (bundle.equalsIgnoreCase(BUNDLE_ANSIBLE)) {
            if (application.equalsIgnoreCase(APP_ANSIBLE_REPORTS)) {
                return new Ansible();
            }
        } else if (bundle.equalsIgnoreCase(CONSOLE)) {
            if (application.equalsIgnoreCase(NOTIFICATIONS)) {
                return new ConsoleNotifications();
            } else if (application.equalsIgnoreCase(SOURCES)) {
                return new Sources();
            }
            if (application.equalsIgnoreCase(RBAC)) {
                return new Rbac();
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
