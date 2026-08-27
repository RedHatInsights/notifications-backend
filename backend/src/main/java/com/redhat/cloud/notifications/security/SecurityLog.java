package com.redhat.cloud.notifications.security;

import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import io.quarkus.logging.Log;
import jakarta.ws.rs.core.SecurityContext;

import java.security.Principal;

/**
 * Structured security event logging for SEC-MON-REQ-1 compliance.
 *
 * <p>All security-relevant operations (CRUD on customer data, authentication
 * failures, authorization failures, process lifecycle) are logged through
 * this utility to ensure the five required fields are always present:</p>
 * <ul>
 *     <li><b>action</b> &mdash; CREATE, READ, UPDATE, DELETE, AUTHENTICATE, AUTHORIZE, STARTUP, SHUTDOWN</li>
 *     <li><b>resource_type</b> &mdash; type of object being operated on</li>
 *     <li><b>resource_id</b> &mdash; identifier of the specific object (or &quot;N/A&quot;)</li>
 *     <li><b>outcome</b> &mdash; success or failure</li>
 *     <li><b>principal</b> &mdash; org_id, user_id, or &quot;system&quot;</li>
 * </ul>
 *
 * <p>Every log line starts with {@code [security_event: true]} so that log
 * aggregation pipelines can filter security events reliably.</p>
 */
public final class SecurityLog {

    private SecurityLog() {
        // utility class
    }

    // ── EOI-1  CRUD on customer data ──────────────────────────────────────

    /**
     * Logs a successful CRUD operation on a resource.
     *
     * @param action       one of CREATE, UPDATE, DELETE
     * @param resourceType the type of the resource (e.g. "integration", "behavior_group")
     * @param resourceId   the identifier of the resource
     * @param sec          the security context of the request
     * @param detail       a short human-readable description
     */
    public static void logCrudSuccess(String action, String resourceType, String resourceId, SecurityContext sec, String detail) {
        Log.infof("[security_event: true][action: %s][resource_type: %s][resource_id: %s][outcome: success][principal: %s] %s",
            action, resourceType, resourceId, principalFromContext(sec), detail);
    }

    /**
     * Logs a failed CRUD operation on a resource.
     *
     * @param action       one of CREATE, UPDATE, DELETE
     * @param resourceType the type of the resource
     * @param resourceId   the identifier of the resource (may be "N/A" if unknown)
     * @param sec          the security context of the request
     * @param reason       the reason for the failure
     */
    public static void logCrudFailure(String action, String resourceType, String resourceId, SecurityContext sec, String reason) {
        Log.warnf("[security_event: true][action: %s][resource_type: %s][resource_id: %s][outcome: failure][principal: %s][reason: %s] Operation failed",
            action, resourceType, resourceId, principalFromContext(sec), reason);
    }

    // ── EOI-7  Authentication failures ────────────────────────────────────

    /**
     * Logs an authentication failure.
     *
     * @param authMethod the authentication method (e.g. "x-rh-identity")
     * @param reason     the reason for the failure
     */
    public static void logAuthFailure(String authMethod, String reason) {
        Log.warnf("[security_event: true][action: AUTHENTICATE][resource_type: session][resource_id: N/A][outcome: failure][principal: anonymous][auth_method: %s][reason: %s] Authentication failed",
            authMethod, reason);
    }

    // ── EOI-8  Authorization failures ─────────────────────────────────────

    /**
     * Logs an authorization (permission) failure.
     *
     * <p>Logged at INFO level because authorization denials are normal
     * operational events (users accessing endpoints they lack permissions for)
     * and do not require engineering action.</p>
     *
     * @param resourceType       the type of resource being accessed
     * @param requiredPermission the permission that was required
     * @param sec                the security context of the request
     */
    public static void logAuthzFailure(String resourceType, String requiredPermission, SecurityContext sec) {
        Log.infof("[security_event: true][action: AUTHORIZE][resource_type: %s][resource_id: N/A][outcome: failure][principal: %s][required_permission: %s] Authorization denied",
            resourceType, principalFromContext(sec), requiredPermission);
    }

    // ── EOI-5  Process lifecycle ──────────────────────────────────────────

    /**
     * Logs a process lifecycle event (startup/shutdown).
     *
     * @param action  STARTUP or SHUTDOWN
     * @param service the service name
     * @param outcome "success" or "failure"
     * @param detail  a short human-readable description
     */
    public static void logLifecycle(String action, String service, String outcome, String detail) {
        Log.infof("[security_event: true][action: %s][resource_type: service][resource_id: %s][outcome: %s][principal: system] %s",
            action, service, outcome, detail);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /**
     * Extracts a loggable principal string from the security context.
     * Returns "org_id=X, user_id=Y" when available, or "anonymous" if the
     * principal is not an {@link RhIdPrincipal}.
     */
    static String principalFromContext(SecurityContext sec) {
        if (sec == null) {
            return "anonymous";
        }
        Principal principal = sec.getUserPrincipal();
        if (principal instanceof RhIdPrincipal rhIdPrincipal) {
            String orgId = rhIdPrincipal.getOrgId();
            String userId = rhIdPrincipal.getIdentity().getUserId();
            return "org_id=" + (orgId != null ? orgId : "unknown")
                + ", user_id=" + (userId != null ? userId : "unknown");
        }
        return "anonymous";
    }
}
