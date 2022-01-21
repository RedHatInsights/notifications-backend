package com.redhat.cloud.notifications.health;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class KafkaConsumedTotalChecker {

    private static final Logger LOGGER = Logger.getLogger(KafkaConsumedTotalChecker.class);
    private static final String COUNTER_NAME = "kafka.consumer.fetch.manager.records.consumed.total";
    private static final String INGRESS_TOPIC = "platform.notifications.ingress";

    @ConfigProperty(name = "notifications.kafka-consumed-total-checker.enabled", defaultValue = "false")
    boolean enabled;

    @Inject
    MeterRegistry meterRegistry;

    private FunctionCounter consumedTotalCounter;
    private double previousTotal;
    private boolean down;

    @PostConstruct
    void postConstruct() {
        consumedTotalCounter = meterRegistry.find(COUNTER_NAME)
                .tag("client.id", "kafka-consumer-ingress")
                .functionCounter();
        if (consumedTotalCounter == null) {
            // This will help prevent a NPE throw if the metrics happens to change in our dependency.
            LOGGER.warnf("%s counter not found, %s is disabled", COUNTER_NAME, KafkaConsumedTotalChecker.class.getSimpleName());
            enabled = false;
        }
    }

    @Scheduled(every = "${notifications.kafka-consumed-total-checker.period:5m}", delayed = "${notifications.kafka-consumed-total-checker.initial-delay:5m}")
    public void periodicCheck() {
        if (enabled) {
            double currentTotal = consumedTotalCounter.count();
            if (currentTotal == previousTotal) {
                LOGGER.debugf("Kafka records consumed total check failed for topic '%s'", INGRESS_TOPIC);
                down = true;
            }
            previousTotal = currentTotal;
        }
    }

    public boolean isDown() {
        return down;
    }
}
