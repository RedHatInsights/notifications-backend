package com.redhat.cloud.notifications.config;

import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.stream.Stream;

public class KesselConfigInterceptorTest {
    private final KesselConfigInterceptor kesselConfigInterceptor = new KesselConfigInterceptor();

    /**
     * Test cases which make sure that the configuration interceptor only
     * overrides the configuration properties when they're Kessel properties
     * and their values are URLs.
     * @return the stream of arguments to try.
     */
    private static Stream<Arguments> testCases() {
        return Stream.of(
            Arguments.of("another-property-which-should-not-be-overwritten", "value", "value"),
            Arguments.of("another-property-which-should-not-be-overwritten", "http://kessel-url:8000", "http://kessel-url:8000"),
            Arguments.of("another-property-which-should-not-be-overwritten", "https://kessel-url:8000", "https://kessel-url:8000"),
            Arguments.of("inventory-api.target-url", "invalidUrl", "invalidUrl"),
            Arguments.of("inventory-api.target-url", "localhost:8000", "localhost:8000"),
            Arguments.of("inventory-api.target-url", "http://kessel-inventory-api.url.test:8000", "kessel-inventory-api.url.test:9000"),
            Arguments.of("inventory-api.target-url", "https://kessel-inventory-api-secure.url.test:8000", "kessel-inventory-api-secure.url.test:9000"),
            Arguments.of("relations-api.target-url", "invalidUrl", "invalidUrl"),
            Arguments.of("relations-api.target-url", "localhost:8000", "localhost:8000"),
            Arguments.of("relations-api.target-url", "http://kessel-relations-api.url.test:8000", "kessel-relations-api.url.test:9000"),
            Arguments.of("relations-api.target-url", "https://kessel-relations-api-secure.url.test:8000", "kessel-relations-api-secure.url.test:9000")
        );
    }

    /**
     * Tests that the Kessel properties are overriden when the property values
     * are URLs.
     * @param configurationPropertyName the configuration property name that
     *                                  might get overriden.
     * @param configurationPropertyValue the original configuration property
     *                                   value as it would be read from the
     *                                   properties files or the environment
     *                                   variables.
     * @param expectedFinalValue the expected final value of the configuration
     *                           property after it has been overriden or not.
     */
    @MethodSource("testCases")
    @ParameterizedTest
    void testConfigurationOverrides(final String configurationPropertyName, final String configurationPropertyValue, final String expectedFinalValue) {
        Assertions.assertEquals(
            expectedFinalValue,
            this.kesselConfigInterceptor.getValue(this.mockConfigSourceInterceptorContext(configurationPropertyName, configurationPropertyValue), configurationPropertyName).getValue(),
            "unexpected configuration property value received"
        );
    }

    /**
     * Mocks the configuration source context.
     * @param configurationPropertyName the property name that is simulated to
     *                                  be processed.
     * @param configurationPropertyValue the original property value that is
     *                                   simulated to be read from properties
     *                                   or environment values.
     * @return the built configuration source interceptor context.
     */
    private ConfigSourceInterceptorContext mockConfigSourceInterceptorContext(final String configurationPropertyName, final String configurationPropertyValue) {
        final ConfigSourceInterceptorContext configSourceInterceptorContext = Mockito.mock(ConfigSourceInterceptorContext.class);

        final ConfigValue configValue = ConfigValue.builder().withValue(configurationPropertyValue).build();
        Mockito.when(configSourceInterceptorContext.proceed(configurationPropertyName)).thenReturn(configValue);

        return configSourceInterceptorContext;
    }
}
