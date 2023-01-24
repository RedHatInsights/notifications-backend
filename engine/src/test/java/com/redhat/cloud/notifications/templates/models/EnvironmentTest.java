package com.redhat.cloud.notifications.templates.models;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

@QuarkusTest
public class EnvironmentTest {

    @Inject
    Environment environment;

    /**
     * Tests that the default value for the "Environment" is "local-dev".
     */
    @Test
    void testEnvironmentDefaultValue() {
        final String expectedValue = "local-dev";

        Assertions.assertEquals(expectedValue, this.environment.name(), "unexpected default value for the environment's name");
    }

    /**
     * Tests that the default value for the "URL" is "/".
     */
    @Test
    void testUrlDefaultValue() {
        final String expectedValue = "/";

        Assertions.assertEquals(expectedValue, this.environment.url(), "unexpected default value for the environment's URL");
    }
}
