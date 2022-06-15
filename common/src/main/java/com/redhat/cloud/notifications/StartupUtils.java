package com.redhat.cloud.notifications;

import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.regex.Pattern;

@ApplicationScoped
public class StartupUtils {

    public static final Pattern ACCESS_LOG_FILTER_PATTERN = Pattern.compile(".*(/health(/\\w+)?|/metrics) HTTP/[0-9].[0-9]\" 200.*\\n?");

    @ConfigProperty(name = "quarkus.http.access-log.category")
    String accessLogCategory;

    public void initAccessLogFilter() {
        java.util.logging.Logger.getLogger(accessLogCategory).setFilter(logRecord ->
                !ACCESS_LOG_FILTER_PATTERN.matcher(logRecord.getMessage()).matches()
        );
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

    public void logExternalServiceUrl(String configKey) {
        Log.infof("%s=%s", configKey, ConfigProvider.getConfig().getValue(configKey, String.class));
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
