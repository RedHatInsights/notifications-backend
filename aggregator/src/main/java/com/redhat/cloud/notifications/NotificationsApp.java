package com.redhat.cloud.notifications;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@QuarkusMain
public class NotificationsApp implements QuarkusApplication {

    private static final String FILTER_REGEX = ".*(/health(/\\w+)?|/metrics) HTTP/[0-9].[0-9]\" 200.*\\n?";
    private static final Pattern PATTERN = Pattern.compile(FILTER_REGEX);

    private static final Logger LOG = Logger.getLogger(NotificationsApp.class);

    @ConfigProperty(name = "quarkus.http.access-log.category")
    String loggerName;

    @Inject
    DailyEmailAggregationJob dailyEmailAggregationJob;

    @Override
    public int run(String... args) {
        initAccessLogFilter();

        LOG.info(readGitProperties());

        dailyEmailAggregationJob.processDailyEmail();

        return 0;
    }

    private void initAccessLogFilter() {
        java.util.logging.Logger accessLog = java.util.logging.Logger.getLogger(loggerName);
        accessLog.setFilter(record -> {
            final String logMessage = record.getMessage();
            Matcher matcher = PATTERN.matcher(logMessage);
            return !matcher.matches();
        });
    }

    private String readGitProperties() {
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream("git.properties")) {
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
}
