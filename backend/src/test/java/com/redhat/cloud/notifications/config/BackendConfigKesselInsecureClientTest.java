package com.redhat.cloud.notifications.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

public class BackendConfigKesselInsecureClientTest {

    /**
     * Test cases for the Kessel insecure client resolution: the Clowder V2
     * {@code authenticated} flag wins when present, otherwise the static
     * insecure client flag is used.
     * @return the stream of arguments to try.
     */
    private static Stream<Arguments> testCases() {
        return Stream.of(
            Arguments.of(Optional.empty(), false, false),
            Arguments.of(Optional.empty(), true, true),
            Arguments.of(Optional.of(true), false, false),
            Arguments.of(Optional.of(true), true, false),
            Arguments.of(Optional.of(false), false, true),
            Arguments.of(Optional.of(false), true, true)
        );
    }

    @MethodSource("testCases")
    @ParameterizedTest
    void testIsKesselInsecureClientEnabled(final Optional<Boolean> authenticated, final boolean insecureClientEnabled, final boolean expected) {
        final BackendConfig config = new BackendConfig();
        config.kesselAuthenticated = authenticated;
        config.kesselInsecureClientEnabled = insecureClientEnabled;

        Assertions.assertEquals(expected, config.isKesselInsecureClientEnabled());
    }
}
