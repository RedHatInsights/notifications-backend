package com.redhat.cloud.notifications;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;

// TODO NOTIF-603 Remove that config after we are using orgId everywhere
@ApplicationScoped
public class OrgIdConfig {

    public static final String USE_ORG_ID = "notifications.use-org-id";

    @ConfigProperty(name = USE_ORG_ID, defaultValue = "false")
    public boolean useOrgId;

    public boolean isUseOrgId() {
        return useOrgId;
    }
}
