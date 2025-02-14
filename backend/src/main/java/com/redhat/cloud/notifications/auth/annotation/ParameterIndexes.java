package com.redhat.cloud.notifications.auth.annotation;

import java.util.Optional;

/**
 * Holds the required parameter indexes to be able to perform the proper
 * authorizations in the {@link AuthorizationInterceptor}.
 */
class ParameterIndexes {
    private Integer securityContextIndex;
    private Integer integrationIdIndex;

    ParameterIndexes() { }

    public Optional<Integer> getSecurityContextIndex() {
        return Optional.ofNullable(this.securityContextIndex);
    }

    public void setSecurityContextIndex(final Integer securityContextIndex) {
        this.securityContextIndex = securityContextIndex;
    }

    public Optional<Integer> getIntegrationIdIndex() {
        return Optional.ofNullable(this.integrationIdIndex);
    }

    public void setIntegrationIdIndex(final Integer integrationIdIndex) {
        this.integrationIdIndex = integrationIdIndex;
    }
}
