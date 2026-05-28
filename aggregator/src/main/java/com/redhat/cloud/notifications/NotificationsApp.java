package com.redhat.cloud.notifications;

import io.quarkus.logging.Log;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@QuarkusMain
public class NotificationsApp implements QuarkusApplication {

    @Inject
    DailyEmailAggregationJob dailyEmailAggregationJob;

    @Override
    public int run(String... args) {
        // Startup - SEC-MON-REQ-1 compliance (EOI-5 process_status)
        Log.infof("[action: STARTUP][resource_type: email_aggregator][principal: system][outcome: success] Email aggregator starting");

        Log.info(readGitProperties());

        dailyEmailAggregationJob.processDailyEmail();

        // Graceful shutdown - SEC-MON-REQ-1 compliance (EOI-5 process_status)
        Log.infof("[action: SHUTDOWN][resource_type: email_aggregator][principal: system][outcome: success] Email aggregator shutting down gracefully");

        return 0;
    }

    private String readGitProperties() {
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream("git.properties")) {
            return readFromInputStream(inputStream);
        } catch (IOException e) {
            Log.error("Could not read git.properties.", e);
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
