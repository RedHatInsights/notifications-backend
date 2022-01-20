package com.redhat.cloud.notifications;

import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import javax.enterprise.event.Observes;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationsApp {

    public static final String FILTER_REGEX = ".*(/health(/\\w+)?|/metrics) HTTP/[0-9].[0-9]\" 200.*\\n?";
    private static final Pattern pattern = Pattern.compile(FILTER_REGEX);

    @ConfigProperty(name = "quarkus.http.access-log.category")
    String loggerName;

    private static final Logger LOG = Logger.getLogger(NotificationsApp.class);

    // we do need a event as parameter here, otherwise the init method won't get called.
    void init(@Observes StartupEvent ev) {
        disableRestClientContextualErrors();

        initAccessLogFilter();

        LOG.info(readGitProperties());

        logExternalServiceUrl("quarkus.rest-client.rbac-authentication.url");
        logExternalServiceUrl("quarkus.rest-client.rbac-s2s.url");
    }

    private void disableRestClientContextualErrors() {
        /*
         * This may become a full-fledged Quarkus configuration key in the future.
         * See https://github.com/quarkusio/quarkus/issues/22777
         * TODO Replace the following line with an application.properties entry when that happens.
         */
        System.setProperty("quarkus.rest-client.disable-contextual-error-messages", "true");
    }

    private void initAccessLogFilter() {
        java.util.logging.Logger accessLog = java.util.logging.Logger.getLogger(loggerName);
        accessLog.setFilter(record -> {
            final String logMessage = record.getMessage();
            Matcher matcher = pattern.matcher(logMessage);
            return !matcher.matches();
        });
    }

    private String readGitProperties() {
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream("git.properties");
        try {
            return readFromInputStream(inputStream);
        } catch (IOException e) {
            LOG.error("Could not read git.properties.", e);
            return "Version information could not be retrieved";
        }
    }

    private String readFromInputStream(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "git.properties file not available";
        }
        StringBuilder resultStringBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("#Generated")) {
                    resultStringBuilder.append(line);
                }
            }
        }
        return resultStringBuilder.toString();
    }

    private void logExternalServiceUrl(String configKey) {
        LOG.infof(configKey + "=%s", ConfigProvider.getConfig().getValue(configKey, String.class));
    }
}
