package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.FeatureFlipper;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class OrgIdHelper {

    @Inject
    FeatureFlipper featureFlipper;

    public boolean useOrgId(String orgId) {
        return featureFlipper.isUseOrgId() && orgId != null && !orgId.isBlank();
    }
}
