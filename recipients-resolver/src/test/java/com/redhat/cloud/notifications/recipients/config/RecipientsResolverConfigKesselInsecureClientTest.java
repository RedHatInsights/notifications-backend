package com.redhat.cloud.notifications.recipients.config;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.stream.Stream;

public class RecipientsResolverConfigKesselInsecureClientTest {

    private static final String CLOWDER_KESSEL_AUTHENTICATED = "clowder.optional-dependency-endpoints.kessel-inventory.api.authenticated";

    /**
     * Test cases for the Kessel insecure client resolution. The manual
     * override wins, then the Clowder V2 {@code authenticated} flag, then the
     * static insecure client flag.
     * @return the stream of arguments to try.
     */
    private static Stream<Arguments> testCases() {
        return Stream.of(
            // Nothing set: the static flag decides.
            Arguments.of(Optional.empty(), Optional.empty(), false, false),
            Arguments.of(Optional.empty(), Optional.empty(), true, true),
            // Clowder flag set: it wins over the static flag.
            Arguments.of(Optional.empty(), Optional.of(true), true, false),
            Arguments.of(Optional.empty(), Optional.of(false), false, true),
            // Manual override set: it wins over everything.
            Arguments.of(Optional.of(true), Optional.of(false), true, false),
            Arguments.of(Optional.of(false), Optional.of(true), false, true)
        );
    }

    @MethodSource("testCases")
    @ParameterizedTest
    void testIsKesselInsecureClientEnabled(final Optional<Boolean> override, final Optional<Boolean> clowder, final boolean insecureClientEnabled, final boolean expected) {
        final RecipientsResolverConfig config = new RecipientsResolverConfig();
        config.kesselAuthenticated = override;
        config.clowderKesselAuthenticated = clowder;
        config.kesselInsecureClientEnabled = insecureClientEnabled;

        Assertions.assertEquals(expected, config.isKesselInsecureClientEnabled());
    }

    @Test
    void testReadClowderKesselAuthenticated() {
        final Config config = Mockito.mock(Config.class);
        Mockito.when(config.getOptionalValue(CLOWDER_KESSEL_AUTHENTICATED, Boolean.class)).thenReturn(Optional.of(false));

        Assertions.assertEquals(Optional.of(false), RecipientsResolverConfig.readClowderKesselAuthenticated(config));
    }

    /**
     * clowder-quarkus-config-source throws when the V2 entry has a URI but no
     * {@code authenticated} flag. The lookup must not prevent startup.
     */
    @Test
    void testReadClowderKesselAuthenticatedIgnoresInvalidEntry() {
        final Config config = Mockito.mock(Config.class);
        Mockito.when(config.getOptionalValue(CLOWDER_KESSEL_AUTHENTICATED, Boolean.class))
            .thenThrow(new IllegalStateException("Missing V2 authenticated flag for kessel-inventory/api"));

        Assertions.assertEquals(Optional.empty(), RecipientsResolverConfig.readClowderKesselAuthenticated(config));
    }
}
