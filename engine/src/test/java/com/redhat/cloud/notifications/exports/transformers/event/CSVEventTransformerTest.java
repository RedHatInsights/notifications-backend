package com.redhat.cloud.notifications.exports.transformers.event;

import com.redhat.cloud.notifications.exports.ResultsTransformer;
import com.redhat.cloud.notifications.exports.TransformationException;
import com.redhat.cloud.notifications.exports.transformers.TransformersHelpers;
import com.redhat.cloud.notifications.models.Event;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CSVEventTransformerTest {
    /**
     * Tests that for a list of events, a correct CSV output is generated.
     * @throws IOException if the expected CSV file cannot be read.
     * @throws TransformationException if any unexpected error occurs during
     * the transformation of the events.
     * @throws URISyntaxException if the URL of the expected CSV file is not
     * valid.
     */
    @Test
    void testTransform() throws IOException, URISyntaxException, TransformationException {
        // Load the expected output for the transformer.
        final URL csvResourceUrl = this.getClass().getResource("/resultstransformers/event/expectedResult.csv");
        Assertions.assertNotNull(csvResourceUrl, "the CSV file with the expected result was not located");

        final String expectedContents = Files.readString(Path.of(csvResourceUrl.toURI()));

        // Build a set of events that will be transformed.
        final List<Event> events = TransformersHelpers.getFixtureEvents();

        // Call the function under test.
        final ResultsTransformer<Event> resultsTransformer = new CSVEventTransformer();
        final String result = resultsTransformer.transform(events);

        Assertions.assertEquals(expectedContents, result, "unexpected CSV transformation performed");
    }
}
