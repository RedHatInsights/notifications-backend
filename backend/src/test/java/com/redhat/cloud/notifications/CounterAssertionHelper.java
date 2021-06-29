package com.redhat.cloud.notifications;

import io.micrometer.core.instrument.MeterRegistry;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MeterRegistry#clear()} cannot be called between tests to reset counters because it would remove the counters
 * instances from the registry while we use many {@link ApplicationScoped} beans which hold references pointing at the
 * aforementioned counters instances. That's why we need this helper to assert increments in a reliable way.
 */
@ApplicationScoped
public class CounterAssertionHelper {

    @Inject
    MeterRegistry registry;

    private final Map<String, Double> counterValuesBeforeTest = new HashMap<>();

    public void saveCounterValuesBeforeTest(String... counterNames) {
        for (String counterName : counterNames) {
            counterValuesBeforeTest.put(counterName, registry.counter(counterName).count());
        }
    }

    public void assertIncrement(String counterName, double expectedIncrement) {
        double actualIncrement = registry.counter(counterName).count() - counterValuesBeforeTest.getOrDefault(counterName, 0d);
        assertEquals(expectedIncrement, actualIncrement);
    }

    public void clear() {
        counterValuesBeforeTest.clear();
    }
}
