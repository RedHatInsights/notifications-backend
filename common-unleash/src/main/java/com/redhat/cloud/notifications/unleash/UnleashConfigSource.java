package com.redhat.cloud.notifications.unleash;

import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.Map;
import java.util.Set;

public class UnleashConfigSource implements ConfigSource {

    /*
     * This config source has a lower priority than the application.properties file.
     * See https://quarkus.io/guides/config-reference#configuration-sources for more details about the ordinal.
     */
    private static final int ORDINAL = 240;

    private static final Map<String, String> CONFIG = Map.of(
        "quarkus.unleash.name-prefix", "notifications",
        "quarkus.unleash.synchronous-fetch-on-initialisation", "true"
    );

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public Map<String, String> getProperties() {
        return CONFIG;
    }

    @Override
    public Set<String> getPropertyNames() {
        return CONFIG.keySet();
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }

    @Override
    public String getValue(String propertyName) {
        return CONFIG.get(propertyName);
    }
}
