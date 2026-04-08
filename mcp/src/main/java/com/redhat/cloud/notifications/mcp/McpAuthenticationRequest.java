package com.redhat.cloud.notifications.mcp;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

public class McpAuthenticationRequest extends BaseAuthenticationRequest {

    private final String xRhIdentityHeaderValue;

    public McpAuthenticationRequest(String xRhIdentityHeaderValue) {
        this.xRhIdentityHeaderValue = xRhIdentityHeaderValue;
    }

    public String getXRhIdentityHeaderValue() {
        return xRhIdentityHeaderValue;
    }
}
