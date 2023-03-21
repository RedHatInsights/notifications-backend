package com.redhat.cloud.notifications.processors.slack;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * This test profile can be used to test a Camel route that could otherwise not be tested
 * because of side effects between several tests on the same route.
 */
public class IsolatedCamelContextTestProfile implements QuarkusTestProfile {
}
