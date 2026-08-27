package com.redhat.cloud.notifications.security;

import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.SecurityContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the {@link SecurityLog} utility class.
 *
 * <p>Verifies the principal extraction logic used by all security log
 * methods. The log methods themselves are thin wrappers around
 * {@code Log.infof}/{@code Log.warnf} with fixed format strings and are
 * exercised end-to-end via integration tests (e.g.
 * {@code AuthorizationInterceptorTest}, {@code EndpointResourceTest}).</p>
 */
class SecurityLogTest {

    @Test
    void principalFromContextReturnsAnonymousWhenNull() {
        assertEquals("anonymous", SecurityLog.principalFromContext(null));
    }

    @Test
    void principalFromContextReturnsAnonymousWhenNotRhIdPrincipal() {
        SecurityContext sec = mock(SecurityContext.class);
        when(sec.getUserPrincipal()).thenReturn(() -> "generic-principal");
        assertEquals("anonymous", SecurityLog.principalFromContext(sec));
    }

    @Test
    void principalFromContextExtractsOrgAndUserId() {
        RhIdentity identity = mock(RhIdentity.class);
        when(identity.getOrgId()).thenReturn("org-123");
        when(identity.getUserId()).thenReturn("user-456");

        RhIdPrincipal principal = new RhIdPrincipal(identity);

        SecurityContext sec = mock(SecurityContext.class);
        when(sec.getUserPrincipal()).thenReturn(principal);

        String result = SecurityLog.principalFromContext(sec);
        assertEquals("org_id=org-123, user_id=user-456", result);
    }

    @Test
    void principalFromContextHandlesNullOrgAndUserId() {
        RhIdentity identity = mock(RhIdentity.class);
        when(identity.getOrgId()).thenReturn(null);
        when(identity.getUserId()).thenReturn(null);

        RhIdPrincipal principal = new RhIdPrincipal(identity);

        SecurityContext sec = mock(SecurityContext.class);
        when(sec.getUserPrincipal()).thenReturn(principal);

        String result = SecurityLog.principalFromContext(sec);
        assertEquals("org_id=unknown, user_id=unknown", result);
    }
}
