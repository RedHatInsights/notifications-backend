package com.redhat.cloud.notifications.unleash;

import io.getunleash.UnleashContext;

public class UnleashContextBuilder {

    public static UnleashContext buildUnleashContextWithOrgId(String orgId) {
        UnleashContext.Builder builder = UnleashContext.builder();
        if (orgId != null) {
            builder.addProperty("orgId", orgId);
        }
        return builder.build();
    }
}
