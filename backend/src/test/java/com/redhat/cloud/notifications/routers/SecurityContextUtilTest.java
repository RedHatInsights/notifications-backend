package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.auth.principal.ConsolePrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import com.redhat.cloud.notifications.auth.principal.turnpike.TurnpikePrincipal;
import com.redhat.cloud.notifications.auth.principal.turnpike.TurnpikeSamlIdentity;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.Principal;

public class SecurityContextUtilTest {
    /**
     * Test that the {@link RhIdentity} is correctly extracted from a security
     * context.
     */
    @Test
    void testExtractRhIdentity() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = Mockito.mock(SecurityContext.class);

        // Create a RhIdentity principal and assign it to the mocked security
        // context.
        final RhIdentity identity = Mockito.mock(RhIdentity.class);
        Mockito.when(identity.getName()).thenReturn("Red Hat user");

        final ConsolePrincipal<?> principal = new RhIdPrincipal(identity);
        Mockito.when(mockedSecurityContext.getUserPrincipal()).thenReturn(principal);

        // Call the function under test.
        final RhIdentity result = SecurityContextUtil.extractRhIdentity(mockedSecurityContext);

        // Assert that the objects are the same. Just by checking the object's
        // reference we can be sure that our stubbed principal above is the
        // one that was extracted.
        Assertions.assertEquals(
            identity,
            result,
            "the extracted identity object was not the same"
        );
    }

    /**
     * Test that when a "non-console" principal is extracted from the security
     * context, an exception is raised.
     */
    @Test
    void testExtractRhIdentityNoConsolePrincipalThrowsException() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = Mockito.mock(SecurityContext.class);

        // Mock a generic principal and make the context return it when asked
        // for it.
        final Principal mockedPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockedSecurityContext.getUserPrincipal()).thenReturn(Mockito.mock(Principal.class));

        // Call the function under test.
        final IllegalStateException e = Assertions.assertThrows(
            IllegalStateException.class,
            () -> SecurityContextUtil.extractRhIdentity(mockedSecurityContext)
        );

        // Assert that the correct exception has been thrown.
        Assertions.assertEquals(
            String.format("unable to extract RH Identity object from principal. Expected \"Console Principal\" object type, got \"%s\"", mockedPrincipal.getClass().getName()),
            e.getMessage(),
            "unexpected exception message"
        );
    }

    /**
     * Test that a "non-RhIdentity" identity inside a principal raises an
     * exception.
     */
    @Test
    void testExtractRhIdentityNoSupportedIdentityThrowsException() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = Mockito.mock(SecurityContext.class);

        // Mock an unexpected identity which should trigger an exception.
        final TurnpikeSamlIdentity turnpikeSamlIdentity = new TurnpikeSamlIdentity();
        turnpikeSamlIdentity.associate = new TurnpikeSamlIdentity.Associate();
        turnpikeSamlIdentity.associate.email = "example@redhat.com";
        turnpikeSamlIdentity.type = "turnpike";

        // Make the identity part of the principal.
        final ConsolePrincipal<?> turnpikePrincipal = new TurnpikePrincipal(turnpikeSamlIdentity);
        Mockito.when(mockedSecurityContext.getUserPrincipal()).thenReturn(turnpikePrincipal);

        // Call the function under test.
        final IllegalStateException e = Assertions.assertThrows(
            IllegalStateException.class,
            () -> SecurityContextUtil.extractRhIdentity(mockedSecurityContext)
        );

        // Assert that the correct exception has been thrown.
        Assertions.assertEquals(
            String.format("unable to extract RH Identity object from principal. Expected \"RhIdentity\" object type, got \"%s\"", turnpikeSamlIdentity.getClass().getName()),
            e.getMessage(),
            "unexpected exception message"
        );
    }
}
