package com.redhat.cloud.notifications.unleash;

import io.getunleash.UnleashContext;

public class UnleashContextBuilder {

    public static UnleashContext buildUnleashContextWithOrgId(String orgId) {
        UnleashContext unleashContext = UnleashContext.builder()
            .addProperty("orgId", orgId)
            .build();
        return unleashContext;
    }
}
