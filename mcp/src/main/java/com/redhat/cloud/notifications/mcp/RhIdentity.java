package com.redhat.cloud.notifications.mcp;

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
 */
public record RhIdentity(
        String type,
        String orgId,
        String accountId,
        String userId,
        String username
) {
}
