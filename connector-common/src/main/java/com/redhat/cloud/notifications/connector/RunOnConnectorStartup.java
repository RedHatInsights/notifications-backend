package com.redhat.cloud.notifications.connector;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;

@Startup
public class RunOnConnectorStartup {

    @Inject
    ConnectorConfig connectorConfig;

    @PostConstruct
    void postConstruct() {
        logGitProperties();
        connectorConfig.log();
    }

    public void logGitProperties() {
        try {
            Log.info(readGitProperties());
        } catch (Exception e) {
            Log.error("Could not read git.properties", e);
        }
    }

    public String readGitProperties() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("git.properties")) {
            if (inputStream == null) {
                return "git.properties is not available";
            } else {
                StringBuilder result = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (!line.startsWith("#Generated")) {
                            result.append(line);
                        }
                    }
                }
                return result.toString();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
