package com.redhat.cloud.notifications.mcp;

import java.security.Principal;

/**
 * Principal for MCP authenticated requests, carrying the user's identity from x-rh-identity.
 * Provides access to orgId, userId, and other identity attributes for MCP tools.
 */
public class McpPrincipal implements Principal {

    private final RhIdentity rhIdentity;

    public McpPrincipal(RhIdentity rhIdentity) {
        this.rhIdentity = rhIdentity;
    }

    @Override
    public String getName() {
        return rhIdentity.username();
    }

    public String getOrgId() {
        return rhIdentity.orgId();
    }

    public String getUserId() {
        return rhIdentity.userId();
    }

    public String getAccountId() {
        return rhIdentity.accountId();
    }

    public String getType() {
        return rhIdentity.type();
    }

    public RhIdentity getRhIdentity() {
        return rhIdentity;
    }

    @Override
    public String toString() {
        return "McpPrincipal{orgId='" + getOrgId() + "', userId='" + getUserId() + "', username='" + getName() + "'}";
    }
}
