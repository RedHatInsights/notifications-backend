package com.redhat.cloud.notifications;

import io.quarkus.logging.Log;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;

import static java.time.ZoneOffset.UTC;

@QuarkusMain
public class NotificationsApp implements QuarkusApplication {

    @Inject
    DailyEmailAggregationJob dailyEmailAggregationJob;

    @Inject
    WeeklyEmailAggregationJob weeklyEmailAggregationJob;

    @Override
    public int run(String... args) {
        // Startup - SEC-MON-REQ-1 compliance (EOI-5 process_status)
        Log.infof("[action: STARTUP][resource_type: email_aggregator][principal: system][outcome: success] Email aggregator starting");

        Log.info(readGitProperties());

        // Compute reference time once for both daily and weekly jobs (15-minute rounding)
        LocalDateTime referenceTime = computeReferenceTime();
        Log.infof("Using reference time: %s", referenceTime);

        Exception dailyException = null;
        try {
            dailyEmailAggregationJob.searchBuildAndSendAggregationCommands(referenceTime);
        } catch (Exception e) {
            dailyException = e;
            Log.error("Daily email aggregation job failed", e);
        }

        try {
            weeklyEmailAggregationJob.searchBuildAndSendAggregationCommands(referenceTime);
        } catch (Exception e) {
            Log.error("Weekly email aggregation job failed", e);
            if (dailyException != null) {
                RuntimeException combinedException = new RuntimeException("Both daily and weekly email aggregation jobs failed", e);
                combinedException.addSuppressed(dailyException);
                throw combinedException;
            }
            throw e;
        }

        if (dailyException != null) {
            throw new RuntimeException("Daily email aggregation job failed", dailyException);
        }

        // Graceful shutdown - SEC-MON-REQ-1 compliance (EOI-5 process_status)
        Log.infof("[action: SHUTDOWN][resource_type: email_aggregator][principal: system][outcome: success] Email aggregator shutting down gracefully");

        return 0;
    }

    /**
     * Compute the reference time for aggregation jobs with 15-minute rounding.
     * Users can schedule execution time every 15 minutes.
     * If the job executes at 10:26, we round to the scheduled time of 10:15.
     *
     * @return the rounded reference time
     */
    private LocalDateTime computeReferenceTime() {
        final LocalDateTime currentTime = LocalDateTime.now(UTC).withSecond(0).withNano(0);

        // Round to 15-minute boundaries
        if (currentTime.getMinute() < 15) {
            return currentTime.withMinute(0);
        } else if (currentTime.getMinute() < 30) {
            return currentTime.withMinute(15);
        } else if (currentTime.getMinute() < 45) {
            return currentTime.withMinute(30);
        } else {
            return currentTime.withMinute(45);
        }
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
