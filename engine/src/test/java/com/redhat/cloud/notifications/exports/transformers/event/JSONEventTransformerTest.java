package com.redhat.cloud.notifications.exports.transformers.event;

import com.redhat.cloud.notifications.exports.ResultsTransformer;
import com.redhat.cloud.notifications.exports.TransformationException;
import com.redhat.cloud.notifications.exports.transformers.TransformersHelpers;
import com.redhat.cloud.notifications.models.Event;
import io.vertx.core.json.JsonArray;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class JSONEventTransformerTest {
    /**
     * Tests that for a list of events, a correct JSON output is generated.
     * @throws IOException if the expected CSV file cannot be read.
     * @throws TransformationException if any unexpected error occurs during
     * the transformation of the events.
     * @throws URISyntaxException if the URL of the expected CSV file is not valid.
     */
    @Test
    void testTransform() throws IOException, TransformationException, URISyntaxException {
        // Load the expected output for the transformer.
        final URL jsonResourceUrl = this.getClass().getResource("/resultstransformers/event/expectedResult.json");
        Assertions.assertNotNull(jsonResourceUrl, "the JSON file with the expected result was not located");

        final String expectedContents = Files.readString(Path.of(jsonResourceUrl.toURI()));

        // Build a set of events that will be transformed.
        final List<Event> events = TransformersHelpers.getFixtureEvents();

        // Call the function under test.
        final ResultsTransformer<Event> resultsTransformer = new JSONEventTransformer();
        final String result = resultsTransformer.transform(events);

        // Assert that both the expected contents and the result are valid JSON
        // objects.
        final JsonArray expectedJson = new JsonArray(expectedContents);
        final JsonArray resultJson = new JsonArray(result);

        // Encode both prettily so that if an error occurs, it is easier to
        // spot where the problem is.
        Assertions.assertEquals(expectedJson.encodePrettily(), resultJson.encodePrettily(), "unexpected CSV transformation performed");
    }
}
