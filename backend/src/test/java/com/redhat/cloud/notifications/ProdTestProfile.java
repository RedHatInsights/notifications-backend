package com.redhat.cloud.notifications;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Add this profile to a Quarkus test with {@link io.quarkus.test.junit.TestProfile @TestProfile}
 * to activate the {@code prod} profile during the tests execution.
 */
public class ProdTestProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "prod";
    }
}
