package com.redhat.cloud.notifications.recipients.resolver;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * Saves the given counter's value. It's supposed to be called before
     * calling any function that might alter the counters. It only saves the
     * count value for a full "counter name + all tags + values" match.
     * @param counterName the name of the counter for which we want to save the
     *                    values.
     * @param tags the tags for the counter.
     */
    public void saveCounterValueBeforeTestFilteredByTags(final String counterName, final Tags tags) {
        final List<String> tagKeys = tags.stream().map(Tag::getKey).toList();

        final Collection<Counter> counters = this.registry
            .find(counterName)
            .tagKeys(tagKeys)
            .counters();

        for (final Counter counter : counters) {
            final Meter.Id id = counter.getId();

            if (counterName.equals(id.getName()) && this.tagsAreEqual(id.getTags(), tags.stream().toList())) {
                for (final Tag tag : tags) {
                    this.counterValuesBeforeTest.put(counterName + tag.getKey() + tag.getValue(), counter.count());
                }
            }
        }
    }

    /**
     * Asserts that the specified increment was given in the given counter.
     * Both the counter's name and their tags must match to perform the
     * assertion.
     * @param counterName the name of the counter to assert.
     * @param expectedIncrement the expected increment to see in the counter.
     * @param tags the tags for the counter.
     */
    public void assertCounterIncrementFilteredByTags(final String counterName, final double expectedIncrement, final Tags tags) {
        final List<String> tagKeys = tags.stream().map(Tag::getKey).toList();

        final Collection<Counter> counters = this.registry
            .find(counterName)
            .tagKeys(tagKeys)
            .counters();

        boolean atLeastOneMatch = false;
        for (final Counter counter : counters) {
            final Meter.Id id = counter.getId();

            if (counterName.equals(id.getName()) && this.tagsAreEqual(id.getTags(), tags.stream().toList())) {
                atLeastOneMatch = true;

                for (final Tag tag : tags) {
                    final double count = counter.count();
                    final double actualIncrement = count - this.counterValuesBeforeTest.getOrDefault(counterName + tag.getKey() + tag.getValue(), 0D);
                    assertEquals(expectedIncrement, actualIncrement);
                }
            }
        }

        if (!atLeastOneMatch) {
            Assertions.fail("No counters were found with the given name and tags, and no values could be checked.");
        }
    }

    /**
     * Asserts that the given tag lists are equal.
     * @param one one list of tags.
     * @param another another list of tags.
     * @return {@code true} if both collections have the same tags, regardless
     * of the order.
     */
    private boolean tagsAreEqual(final List<Tag> one, final List<Tag> another) {
        return new HashSet<>(one).equals(new HashSet<>(another));
    }
}
