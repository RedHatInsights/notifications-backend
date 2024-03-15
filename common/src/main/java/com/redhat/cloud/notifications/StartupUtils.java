package com.redhat.cloud.notifications;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;

@ApplicationScoped
public class StartupUtils {

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

    public void logExternalServiceUrl(String configKey) {
        String configValue = ConfigProvider.getConfig().getOptionalValue(configKey, String.class).orElse("");
        Log.infof("%s=%s", configKey, configValue);
    }

    public void disableRestClientContextualErrors() {
        /*
         * This may become a full-fledged Quarkus configuration key in the future.
         * See https://github.com/quarkusio/quarkus/issues/22777
         * TODO Replace the following line with an application.properties entry when that happens.
         */
        System.setProperty("quarkus.rest-client.disable-contextual-error-messages", "true");
    }
}
