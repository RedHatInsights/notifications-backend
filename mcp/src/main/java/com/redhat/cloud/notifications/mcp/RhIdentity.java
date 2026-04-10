package com.redhat.cloud.notifications.mcp;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Simplified Red Hat identity record for MCP authentication.
 * Represents the parsed contents of the x-rh-identity header.
 * Only User identities are supported.
 *
 * @param type Identity type (always "User")
 * @param orgId Organization ID (mandatory)
 * @param accountId Account number (legacy field)
 * @param userId User ID (mandatory)
 * @param username Username (mandatory)
 * @param rawHeader The original Base64-encoded x-rh-identity header value, preserved for forwarding to backend calls
 */
public record RhIdentity(
        String type,
        String orgId,
        String accountId,
        String userId,
        String username,
        // Excluded from JSON serialization: this is effectively a bearer credential
        // within the internal network and must not leak via Jackson auto-serialization
        // of this record (e.g. in error responses or debug endpoints).
        @JsonIgnore String rawHeader
) {
    @Override
    public String toString() {
        return "RhIdentity[type=" + type + ", orgId=" + orgId
                + ", accountId=" + accountId + ", userId=" + userId
                + ", username=" + username + "]";
    }
}
