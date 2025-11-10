package com.redhat.cloud.notifications.auth.annotation;

import java.util.Optional;

/**
 * Holds the required parameter indexes to be able to perform the proper
 * authorizations in the {@link AuthorizationInterceptor}.
 */
class ParameterIndexes {
    private Integer securityContextIndex;

    ParameterIndexes() { }

    public Optional<Integer> getSecurityContextIndex() {
        return Optional.ofNullable(this.securityContextIndex);
    }

    public void setSecurityContextIndex(final Integer securityContextIndex) {
        this.securityContextIndex = securityContextIndex;
    }
}
