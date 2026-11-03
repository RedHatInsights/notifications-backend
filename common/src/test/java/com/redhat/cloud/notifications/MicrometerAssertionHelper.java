package com.redhat.cloud.notifications;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

    private final Map<String, Double> counterValuesBeforeTest = new ConcurrentHashMap<>();
    private final Map<String, Long> timerCountsBeforeTest = new ConcurrentHashMap<>();

    public void saveCounterValuesBeforeTest(String... counterNames) {
        for (String counterName : counterNames) {
            counterValuesBeforeTest.put(counterName, registry.counter(counterName).count());
        }
    }

    public void saveCounterValueWithTagsBeforeTest(String counterName, String... tags) {
        Collection<Counter> counters = registry.find(counterName)
            .tags(tags)
            .counters();
        for (Counter counter : counters) {
            Meter.Id id = counter.getId();
            List<String> tagList = new ArrayList<>();
            for (Tag tag : id.getTags()) {
                tagList.add(tag.getKey());
                tagList.add(tag.getValue());
            }
            counterValuesBeforeTest.put(counterName + tagList, registry.counter(counterName, id.getTags()).count());
        }
    }

    public void assertCounterIncrement(String counterName, double expectedIncrement, String... tags) {
        double actualIncrement = registry.counter(counterName, tags).count() - counterValuesBeforeTest.getOrDefault(
                counterName + Arrays.toString(tags), 0D);
        assertEquals(expectedIncrement, actualIncrement);
    }

    public void saveCounterValueFilteredByTagsBeforeTest(String counterName, String tagKeys, String tagValue) {
        Collection<Counter> counters = registry.find(counterName)
            .tagKeys(tagKeys)
            .counters();
        for (Counter counter : counters) {
            Meter.Id id = counter.getId();
            if (id.getTag(tagKeys).equals(tagValue)) {
                counterValuesBeforeTest.put(counterName + tagKeys + tagValue, counter.count());
                break;
            }
        }
    }

    public void assertCounterValueFilteredByTagsIncrement(String counterName, String tagKeys, String tagValue, double expectedIncrement) {
        Collection<Counter> counters = registry.find(counterName)
            .tagKeys(tagKeys)
            .counters();
        for (Counter counter : counters) {
            Meter.Id id = counter.getId();
            if (id.getTag(tagKeys).equals(tagValue)) {
                double actualIncrement = counter.count() - counterValuesBeforeTest.getOrDefault(
                    counterName + tagKeys + tagValue, 0D);
                assertEquals(expectedIncrement, actualIncrement);
                break;
            }
        }
    }

    public void assertCounterIncrement(String counterName, double expectedIncrement) {
        double actualIncrement = registry.counter(counterName).count() - counterValuesBeforeTest.getOrDefault(counterName, 0D);
        assertEquals(expectedIncrement, actualIncrement);
    }

    public void assertCounterIncrementWithTags(String counterName, double expectedIncrement, String... tags) {
        Collection<Counter> counters = registry.find(counterName)
            .tags(tags)
            .counters();
        if (counters.isEmpty()) {
            // Counter was never registered (e.g. never incremented) — only valid when expecting 0.
            assertEquals(0D, expectedIncrement,
                String.format("No counters found for '%s' with tags %s, but expected a non-zero increment", counterName, Arrays.toString(tags)));
            return;
        }
        for (Counter counter : counters) {
            Meter.Id id = counter.getId();
            List<String> tagList = new ArrayList<>();
            for (Tag tag : id.getTags()) {
                tagList.add(tag.getKey());
                tagList.add(tag.getValue());
            }
            double actualIncrement = counter.count() - counterValuesBeforeTest.getOrDefault(counterName + tagList, 0D);
            assertEquals(expectedIncrement, actualIncrement);
        }
    }

    public void awaitAndAssertCounterIncrementWithTags(String counterName, double expectedIncrement, String... tags) {
        await().atMost(Duration.ofSeconds(30L)).until(() -> {
            Collection<Counter> counters = registry.find(counterName).tags(tags).counters();
            if (counters.isEmpty()) {
                return expectedIncrement == 0D;
            }
            for (Counter counter : counters) {
                Meter.Id id = counter.getId();
                List<String> tagList = new ArrayList<>();
                for (Tag tag : id.getTags()) {
                    tagList.add(tag.getKey());
                    tagList.add(tag.getValue());
                }
                double actualIncrement = counter.count() - counterValuesBeforeTest.getOrDefault(counterName + tagList, 0D);
                if (expectedIncrement != actualIncrement) {
                    return false;
                }
            }
            return true;
        });
    }

    public void awaitAndAssertCounterIncrement(String counterName, double expectedIncrement) {
        await().atMost(Duration.ofSeconds(30L)).until(() -> {
            double actualIncrement = registry.counter(counterName).count() - counterValuesBeforeTest.getOrDefault(counterName, 0D);
            return expectedIncrement == actualIncrement;
        });
    }

    public void awaitAndAssertCounterIncrementFilteredByTags(final String counterName, final String tagKey, final String tagValue, final double expectedIncrement) {
        await().atMost(Duration.ofSeconds(30L)).until(() -> {
            double actualIncrement = this.registry.counter(counterName, Tags.of(tagKey, tagValue)).count() - counterValuesBeforeTest.getOrDefault(
                counterName + tagKey + tagValue, 0D);

            return expectedIncrement == actualIncrement;
        });
    }

    public void saveTimerCountFilteredByTagsBeforeTest(String timerName, String tagKey, String tagValue) {
        Timer timer = registry.find(timerName).tag(tagKey, tagValue).timer();
        timerCountsBeforeTest.put(timerName + tagKey + tagValue, timer != null ? timer.count() : 0L);
    }

    public void awaitAndAssertTimerCountFilteredByTagsIncrement(String timerName, String tagKey, String tagValue, long expectedIncrement) {
        await().atMost(Duration.ofSeconds(30L)).until(() -> {
            Timer timer = registry.find(timerName).tag(tagKey, tagValue).timer();
            if (timer == null) {
                return expectedIncrement == 0;
            } else {
                long actualIncrement = timer.count() - timerCountsBeforeTest.getOrDefault(timerName + tagKey + tagValue, 0L);
                return expectedIncrement == actualIncrement;
            }
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
        timerCountsBeforeTest.clear();
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
