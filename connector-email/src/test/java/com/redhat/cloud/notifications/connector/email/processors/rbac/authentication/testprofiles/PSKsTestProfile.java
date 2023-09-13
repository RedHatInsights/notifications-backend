package com.redhat.cloud.notifications.connector.email.processors.rbac.authentication.testprofiles;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.vertx.core.json.JsonObject;

import java.util.Map;

public class PSKsTestProfile implements QuarkusTestProfile {
    public static final String NOTIFICATIONS_PSK_SECRET = "psk-secret-for-notifications";

    /**
     * Sets the {@link EmailConnectorConfig#RBAC_PSKS} to contain the
     * {@link PSKsTestProfile#NOTIFICATIONS_PSK_SECRET} secret in the
     * "notifications" key.
     * @return a configuration override with the configured RBAC PSK for
     * notifications.
     */
    @Override
    public Map<String, String> getConfigOverrides() {
        final JsonObject notifications = new JsonObject();
        notifications.put("secret", NOTIFICATIONS_PSK_SECRET);

        final JsonObject psks = new JsonObject();
        psks.put("notifications", notifications);

        return Map.of(EmailConnectorConfig.RBAC_PSKS, psks.encode());
    }
}
