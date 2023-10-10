package com.redhat.cloud.notifications.connector.email.testprofile;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile for enabling sending a single email for multiple users.
 */
@Deprecated(forRemoval = true)
public class SingleEmailMultipleUsersTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(EmailConnectorConfig.SINGLE_EMAIL_PER_USER, "false");
    }
}
