package com.redhat.cloud.notifications;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MeterRegistry#clear()} cannot be called between tests to reset counters because it would remove the counters
 * instances from the registry while we use many {@link ApplicationScoped} beans which hold references pointing at the
 * aforementioned counters instances. That's why we need this helper to assert increments in a reliable way.
 */
@ApplicationScoped
public class MicrometerAssertionHelper {

    @Inject
    MeterRegistry registry;

    private final Map<String, Double> counterValuesBeforeTest = new HashMap<>();

    public void saveCounterValuesBeforeTest(String... counterNames) {
        for (String counterName : counterNames) {
            counterValuesBeforeTest.put(counterName, registry.counter(counterName).count());
        }
    }

    public void assertCounterIncrement(String counterName, double expectedIncrement) {
        double actualIncrement = registry.counter(counterName).count() - counterValuesBeforeTest.getOrDefault(counterName, 0D);
        assertEquals(expectedIncrement, actualIncrement);
    }

    public void awaitAndAssertCounterIncrement(String counterName, double expectedIncrement) {
        await().atMost(Duration.ofSeconds(30L)).until(() -> {
            double actualIncrement = registry.counter(counterName).count() - counterValuesBeforeTest.getOrDefault(counterName, 0D);
            return expectedIncrement == actualIncrement;
        });
    }

    public void awaitAndAssertTimerIncrement(String timerName, long expectedIncrement) {
        await().atMost(Duration.ofSeconds(30L)).until(() -> {
            Timer timer = findTimerByNameOnly(timerName);
            if (timer == null) {
                // The timer may be created after this method is executed.
                return false;
            } else {
                long actualIncrement = Optional.ofNullable(timer.count()).orElse(0L);
                return expectedIncrement == actualIncrement;
            }
        });
    }

    public void clearSavedValues() {
        counterValuesBeforeTest.clear();
    }

    public void removeDynamicTimer(String timerName) {
        for (Timer timer : findTimersByNameOnly(timerName)) {
            registry.remove(timer);
        }
    }

    /**
     * Finds a timer from its name only, tags are ignored.
     * If multiple timers match the name, the first one will be returned.
     */
    private Timer findTimerByNameOnly(String name) {
        return registry.find(name).timer();
    }

    /**
     * Finds a collection of timers from their name only, tags are ignored.
     */
    private Collection<Timer> findTimersByNameOnly(String name) {
        return registry.find(name).timers();
    }
}
