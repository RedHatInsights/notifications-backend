package com.redhat.cloud.notifications;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.jboss.logging.Logger;
import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@QuarkusMain
public class NotificationsApp implements QuarkusApplication {

    private static final Logger LOG = Logger.getLogger(NotificationsApp.class);

    @Inject
    DailyEmailAggregationJob dailyEmailAggregationJob;

    @Override
    public int run(String... args) {
        LOG.info(readGitProperties());

        dailyEmailAggregationJob.processDailyEmail();

        return 0;
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
