package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.AggregatorConfig;
import com.redhat.cloud.notifications.helpers.AggregationTimeHelper;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.exporter.PushGateway;
import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.Optional;

@QuarkusMain
public class NotificationsApp implements QuarkusApplication {

    @Inject
    DailyEmailAggregationJob dailyEmailAggregationJob;

    @Inject
    WeeklyEmailAggregationJob weeklyEmailAggregationJob;

    @Inject
    AggregatorConfig aggregatorConfig;

    @ConfigProperty(name = "prometheus.pushgateway.url", defaultValue = "http://localhost:8080")
    String prometheusPushGatewayUrl;

    @Override
    public int run(String... args) {
        // Startup - SEC-MON-REQ-1 compliance (EOI-5 process_status)
        Log.infof("[action: STARTUP][resource_type: email_aggregator][principal: system][outcome: success] Email aggregator starting");

        Log.info(readGitProperties());

        // Compute reference time once for both daily and weekly jobs (15-minute rounding)
        LocalDateTime referenceTime = AggregationTimeHelper.computeReferenceTime();
        Log.infof("Using reference time: %s", referenceTime);

        // Create a single shared registry for both jobs to avoid port binding conflicts
        CollectorRegistry registry = new CollectorRegistry();

        DelayedThrower.throwEventually("Email aggregation job(s) failed", accumulator -> {
            try {
                dailyEmailAggregationJob.searchBuildAndSendAggregationCommands(referenceTime, registry);
            } catch (Exception e) {
                Log.error("Daily email aggregation job failed", e);
                accumulator.add(e);
            }

            try {
                weeklyEmailAggregationJob.searchBuildAndSendAggregationCommands(referenceTime, registry);
            } catch (Exception e) {
                Log.error("Weekly email aggregation job failed", e);
                accumulator.add(e);
            }
        });

        // Expose or push metrics once after both jobs complete
        if (!LaunchMode.current().isDevOrTest()) {
            if (aggregatorConfig.isScrapeExportMode()) {
                exposeMetricsForScraping(registry);
            } else {
                pushMetricsToGateway(registry);
            }
        }

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

    protected void pushMetricsToGateway(CollectorRegistry registry) {
        PushGateway pg = new PushGateway(prometheusPushGatewayUrl);
        try {
            pg.pushAdd(registry, "aggregator_job");
        } catch (IOException e) {
            Log.warnf("Could not push aggregator metrics to Prometheus Pushgateway.", e);
        }
    }

    /**
     * Interim fix for RHCLOUD-50496: Pushgateway isn't deployed on HCC clusters yet
     * (RHCLOUD-48965), so instead of pushing, expose the registry on an embedded HTTP
     * server for long enough to be scraped, then let the job exit normally. To be
     * removed once Pushgateway is available on HCC.
     */
    protected void exposeMetricsForScraping(CollectorRegistry registry) {
        Optional<Integer> metricsPort = aggregatorConfig.getMetricsPort();
        if (metricsPort.isEmpty()) {
            return;
        }

        try {
            int keepAliveSeconds = aggregatorConfig.getMetricsScrapeKeepAliveSeconds();
            HTTPServer server = new HTTPServer.Builder()
                    .withPort(metricsPort.get())
                    .withRegistry(registry)
                    .withDaemonThreads(true)
                    .build();
            try {
                Log.infof("Exposing aggregator metrics for scraping on port %d for %d seconds",
                        metricsPort.get(), keepAliveSeconds);
                Thread.sleep(keepAliveSeconds * 1000L);
            } finally {
                server.close();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.warnf("Interrupted while exposing aggregator metrics for scraping.", e);
        } catch (Exception e) {
            Log.warnf("Could not expose aggregator metrics for scraping.", e);
        }
    }
}
