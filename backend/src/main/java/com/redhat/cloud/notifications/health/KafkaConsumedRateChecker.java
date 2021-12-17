package com.redhat.cloud.notifications.health;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class KafkaConsumedRateChecker {

    private static final Logger LOGGER = Logger.getLogger(KafkaConsumedRateChecker.class);
    private static final String INGRESS_TOPIC = "platform.notifications.ingress";

    @ConfigProperty(name = "kafka-consumed-rate-checker.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "kafka-consumed-rate-checker.max-failures", defaultValue = "5")
    int maxFailures;

    @Inject
    MeterRegistry meterRegistry;

    private Gauge consumedRateGauge;
    private int currentFailures;

    @PostConstruct
    void postConstruct() {
        consumedRateGauge = meterRegistry.find("kafka.consumer.fetch.manager.records.consumed.rate")
                .tag("client.id", "kafka-consumer-ingress")
                .gauge();
    }

    @Scheduled(every = "${kafka-consumed-rate-checker.period:1m}", delayed = "${kafka-consumed-rate-checker.initial-delay:5m}")
    public void periodicCheck() {
        if (enabled) {
            if (consumedRateGauge.value() == 0d) {
                currentFailures++;
                LOGGER.debugf("Kafka records consumed rate check failed for topic '%s'", INGRESS_TOPIC);
            } else if (currentFailures > 0) {
                currentFailures = 0;
                LOGGER.debugf("Kafka records consumed rate check succeeded for topic '%s' after %d failures",
                        INGRESS_TOPIC, currentFailures);
            }
        }
    }

    public boolean isDown() {
        return enabled && currentFailures >= maxFailures;
    }
}
