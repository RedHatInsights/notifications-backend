package com.redhat.cloud.notifications.exports;

import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequest;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequestClass;
import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.event.parser.GenericConsoleCloudEvent;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.exports.filters.events.EventFiltersExtractor;
import com.redhat.cloud.notifications.exports.transformers.TransformersHelpers;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import io.vertx.core.json.JsonArray;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.exports.ExportEventListener.EXPORT_CHANNEL;
import static java.nio.charset.StandardCharsets.UTF_8;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ExportEventListenerTest {

    @InjectMock
    EventRepository eventRepository;

    @InjectMock
    @RestClient
    ExportServicePsk exportService;

    @Any
    @Inject
    InMemoryConnector inMemoryConnector;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @AfterEach
    void resetCounterValues() {
        this.micrometerAssertionHelper.clearSavedValues();
    }

    /**
     * Tests that when an export request contains a payload which isn't a valid
     * Cloud Event, then the corresponding error counter is increased.
     */
    @Test
    void testNonParseableCloudEventIncrementsErrorCounter() {
        // Save the counter values to assert the "errors count" change later.
        // Also save the successes counter, to make sure that it doesn't get
        // accidentally incremented.
        this.micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(
            ExportEventListener.EXPORTS_SERVICE_FAILURES_COUNTER,
            ExportEventListener.FAILURE_KEY,
            ExportEventListener.FAILURE_NON_PARSEABLE_CE
        );
        this.micrometerAssertionHelper.saveCounterValuesBeforeTest(ExportEventListener.EXPORTS_SERVICE_SUCCESSES_COUNTER);

        final InMemorySource<String> exportIn = this.inMemoryConnector.source(EXPORT_CHANNEL);
        exportIn.send("Hello, World!");

        // Assert that the errors counter was incremented, and that the
        // successes counter did not increment.
        this.micrometerAssertionHelper.awaitAndAssertCounterIncrementFilteredByTags(
            ExportEventListener.EXPORTS_SERVICE_FAILURES_COUNTER,
            ExportEventListener.FAILURE_KEY,
            ExportEventListener.FAILURE_NON_PARSEABLE_CE,
            1
        );
        this.micrometerAssertionHelper.assertCounterIncrement(ExportEventListener.EXPORTS_SERVICE_SUCCESSES_COUNTER, 0);
    }

    /**
     * Tests that when an export request is received with an invalid resource
     * type, then an error is sent to the export service.
     */
    @Test
    void testInvalidResourceTypeRaisesError() {
        // Save the counter values to assert the "errors count" change later.
        // Also save the successes counter, to make sure that it doesn't get
        // accidentally incremented.
        this.micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(
            ExportEventListener.EXPORTS_SERVICE_FAILURES_COUNTER,
            ExportEventListener.FAILURE_KEY,
            ExportEventListener.FAILURE_UNSUPPORTED_RESOURCE_TYPE
        );
        this.micrometerAssertionHelper.saveCounterValuesBeforeTest(ExportEventListener.EXPORTS_SERVICE_SUCCESSES_COUNTER);

        final InMemorySource<String> exportIn = this.inMemoryConnector.source(EXPORT_CHANNEL);

        // Generate an export request but set a resource type which we don't
        // support.
        final GenericConsoleCloudEvent<ResourceRequest> cee = ExportEventTestHelper.createExportCloudEventFixture(Format.JSON);
        final ResourceRequestClass data = cee.getData().getResourceRequest();
        data.setResource("invalid-type");

        // Serialize the payload and send it to the Kafka topic.
        final ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();
        exportIn.send(consoleCloudEventParser.toJson(cee));

        // Assert that the export service was called as expected.
        this.assertErrorNotificationIsCorrect(HttpStatus.SC_BAD_REQUEST, "the specified resource type is unsupported by this application");

        // Assert that the errors counter was incremented, and that the
        // successes counter did not increment.
        this.micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(
            ExportEventListener.EXPORTS_SERVICE_FAILURES_COUNTER,
            ExportEventListener.FAILURE_KEY,
            ExportEventListener.FAILURE_UNSUPPORTED_RESOURCE_TYPE,
            1
        );
        this.micrometerAssertionHelper.assertCounterIncrement(ExportEventListener.EXPORTS_SERVICE_SUCCESSES_COUNTER, 0);
    }

    /**
     * Tests that when an export request is received and the specified "from"
     * filter is non-parseable, an error is sent to the export service.
     */
    @Test
    void testNonParseableEventsFromFilterRaisesError() {
        // Save the counter values to assert the "errors count" change later.
        // Also save the successes counter, to make sure that it doesn't get
        // accidentally incremented.
        this.micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(
            ExportEventListener.EXPORTS_SERVICE_FAILURES_COUNTER,
            ExportEventListener.FAILURE_KEY,
            ExportEventListener.FAILURE_UNABLE_EXTRACT_FILTERS
        );
        this.micrometerAssertionHelper.saveCounterValuesBeforeTest(ExportEventListener.EXPORTS_SERVICE_SUCCESSES_COUNTER);

        final InMemorySource<String> exportIn = this.inMemoryConnector.source(EXPORT_CHANNEL);

        // Generate an export request but set an invalid "from" filter.
        final GenericConsoleCloudEvent<ResourceRequest> cee = ExportEventTestHelper.createExportCloudEventFixture(Format.JSON);
        final ResourceRequestClass data = cee.getData().getResourceRequest();
        data.setFilters(Map.of("from", "invalid-date"));

        // Serialize the payload and send it to the Kafka topic.
        final ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();
        exportIn.send(consoleCloudEventParser.toJson(cee));

        // Assert that the export service was called as expected.
        this.assertErrorNotificationIsCorrect(HttpStatus.SC_BAD_REQUEST, "unable to parse the 'from' date filter with the 'yyyy-mm-dd' format");

        // Assert that the errors counter was incremented, and that the
        // successes counter did not increment.
        this.micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(
            ExportEventListener.EXPORTS_SERVICE_FAILURES_COUNTER,
            ExportEventListener.FAILURE_KEY,
            ExportEventListener.FAILURE_UNABLE_EXTRACT_FILTERS,
            1
        );
        this.micrometerAssertionHelper.assertCounterIncrement(ExportEventListener.EXPORTS_SERVICE_SUCCESSES_COUNTER, 0);
    }

    /**
     * Tests that when an export request is received and the specified "to"
     * filter is non-parseable, an error is sent to the export service.
     */
    @Test
    void testNonParseableEventsToFilterRaisesError() {
        // Save the counter values to assert the "errors count" change later.
        // Also save the successes counter, to make sure that it doesn't get
        // accidentally incremented.
        this.micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(
            ExportEventListener.EXPORTS_SERVICE_FAILURES_COUNTER,
            ExportEventListener.FAILURE_KEY,
            ExportEventListener.FAILURE_UNABLE_EXTRACT_FILTERS
        );
        this.micrometerAssertionHelper.saveCounterValuesBeforeTest(ExportEventListener.EXPORTS_SERVICE_SUCCESSES_COUNTER);

        final InMemorySource<String> exportIn = this.inMemoryConnector.source(EXPORT_CHANNEL);

        // Generate an export request but set an invalid "to" filter.
        final GenericConsoleCloudEvent<ResourceRequest> cee = ExportEventTestHelper.createExportCloudEventFixture(Format.JSON);
        final ResourceRequestClass data = cee.getData().getResourceRequest();
        data.setFilters(Map.of("to", "invalid-date"));

        // Serialize the payload and send it to the Kafka topic.
        final ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();
        exportIn.send(consoleCloudEventParser.toJson(cee));

        // Assert that the export service was called as expected.
        this.assertErrorNotificationIsCorrect(HttpStatus.SC_BAD_REQUEST, "unable to parse the 'to' date filter with the 'yyyy-mm-dd' format");

        // Assert that the errors counter was incremented, and that the
        // successes counter did not increment.
        this.micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(
            ExportEventListener.EXPORTS_SERVICE_FAILURES_COUNTER,
            ExportEventListener.FAILURE_KEY,
            ExportEventListener.FAILURE_UNABLE_EXTRACT_FILTERS,
            1
        );
        this.micrometerAssertionHelper.assertCounterIncrement(ExportEventListener.EXPORTS_SERVICE_SUCCESSES_COUNTER, 0);
    }

    /**
     * Tests that when an export request is received and the specified filters
     * filter are invalid, an error is sent to the export service.
     */
    @Test
    void testInvalidEventsFiltersRaisesErrors() {
        // Save the counter values to assert the "errors count" change later.
        // Also save the successes counter, to make sure that it doesn't get
        // accidentally incremented.
        this.micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(
            ExportEventListener.EXPORTS_SERVICE_FAILURES_COUNTER,
            ExportEventListener.FAILURE_KEY,
            ExportEventListener.FAILURE_UNABLE_EXTRACT_FILTERS
        );
        this.micrometerAssertionHelper.saveCounterValuesBeforeTest(ExportEventListener.EXPORTS_SERVICE_SUCCESSES_COUNTER);

        record TestCase(LocalDate from, LocalDate to, String expectedErrorMessage) { }

        final List<TestCase> testCases = new ArrayList<>();

        final LocalDate today = LocalDate.now(ZoneOffset.UTC);

        // "From" date in the future.
        testCases.add(
            new TestCase(
                today.plusDays(1),
                null,
                "invalid 'from' filter date specified: the specified date is in the future"
            )
        );

        // "From" date is older than a month.
        testCases.add(
            new TestCase(
                today.minusMonths(1).minusDays(1),
                null,
                "invalid 'from' filter date specified: the specified date is older than a month"
            )
        );

        // "From" date is after the "to" date.
        testCases.add(
            new TestCase(
                today.minusDays(5),
                today.minusDays(10),
                "'from' date must be earlier than the 'to' date"
            )
        );

        // "To" date is in the future.
        testCases.add(
            new TestCase(
                null,
                today.plusDays(1),
                "invalid 'to' filter date specified: the specified date is in the future"
            )
        );

        // "To" date is older than a month.
        testCases.add(
            new TestCase(
                null,
                today.minusMonths(1).minusDays(1),
                "invalid 'to' filter date specified: the specified date is older than a month"
            )
        );

        final InMemorySource<String> exportIn = this.inMemoryConnector.source(EXPORT_CHANNEL);

        for (final TestCase testCase : testCases) {
            // Generate an export request but set a resource type which we don't
            // support.
            final GenericConsoleCloudEvent<ResourceRequest> cee = ExportEventTestHelper.createExportCloudEventFixture(Format.JSON);
            final ResourceRequestClass data = cee.getData().getResourceRequest();
            final Map<String, Object> filters = data.getFilters();
            // Clear the filters to start fresh with them.
            filters.clear();

            if (testCase.from != null) {
                filters.put(EventFiltersExtractor.FILTER_DATE_FROM, testCase.from.toString());
            }

            if (testCase.to != null) {
                filters.put(EventFiltersExtractor.FILTER_DATE_TO, testCase.to.toString());
            }

            // Serialize the payload and send it to the Kafka topic.
            final ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();
            exportIn.send(consoleCloudEventParser.toJson(cee));

            // Assert that the export service was called as expected.
            this.assertErrorNotificationIsCorrect(HttpStatus.SC_BAD_REQUEST, testCase.expectedErrorMessage());

            // Clear the invocations to the mock.
            Mockito.clearInvocations(this.exportService);
        }

        // Assert that the errors counter was incremented, and that the
        // successes counter did not increment.
        this.micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(
            ExportEventListener.EXPORTS_SERVICE_FAILURES_COUNTER,
            ExportEventListener.FAILURE_KEY,
            ExportEventListener.FAILURE_UNABLE_EXTRACT_FILTERS,
            testCases.size()
        );
        this.micrometerAssertionHelper.assertCounterIncrement(ExportEventListener.EXPORTS_SERVICE_SUCCESSES_COUNTER, 0);
    }

    /**
     * Tests that when a valid JSON export request for exporting events is
     * received, then a valid request is sent to the export service, containing
     * the expected body.
     */
    @Test
    void testExportEventsJSON() throws IOException, URISyntaxException {
        // Save the counter values to assert the "successes count" change later.
        // Also save the "errors" counter to assert that it did not
        // accidentally increment.
        this.micrometerAssertionHelper.saveCounterValuesBeforeTest(ExportEventListener.EXPORTS_SERVICE_FAILURES_COUNTER);
        this.micrometerAssertionHelper.saveCounterValuesBeforeTest(ExportEventListener.EXPORTS_SERVICE_SUCCESSES_COUNTER);

        final InMemorySource<String> exportIn = this.inMemoryConnector.source(EXPORT_CHANNEL);

        // Generate an export request but set a resource type which we don't
        // support.
        final GenericConsoleCloudEvent<ResourceRequest> cee = ExportEventTestHelper.createExportCloudEventFixture(Format.JSON);

        // Serialize the payload and send it to the Kafka topic.
        final ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();

        // Return fixture events when the repository is called.
        Mockito.when(this.eventRepository.findEventsToExport(Mockito.eq(DEFAULT_ORG_ID), Mockito.any(), Mockito.any())).thenReturn(TransformersHelpers.getFixtureEvents());

        // Send the JSON payload.
        exportIn.send(consoleCloudEventParser.toJson(cee));

        // Assert that the export service was called as expected.
        final ArgumentCaptor<UUID> capturedExportUuid = ArgumentCaptor.forClass(UUID.class);
        final ArgumentCaptor<String> capturedApplication = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<UUID> capturedResourceUuid = ArgumentCaptor.forClass(UUID.class);
        final ArgumentCaptor<String> capturedJSONContents = ArgumentCaptor.forClass(String.class);

        // Wait at most 10 seconds before failing.
        Mockito.verify(this.exportService, Mockito.timeout(10000).times(1)).uploadJSONExport(capturedExportUuid.capture(), capturedApplication.capture(), capturedResourceUuid.capture(), capturedJSONContents.capture());

        // Assert that the export request's UUID is correct.
        Assertions.assertEquals(ExportEventTestHelper.EXPORT_CE_EXPORT_UUID, capturedExportUuid.getValue(), "unexpected export request UUID sent to the export service");

        // Assert that the export request's application is correct.
        Assertions.assertEquals(URLEncoder.encode(ExportEventListener.APPLICATION_NAME, UTF_8), capturedApplication.getValue(), "unexpected application's name sent to the export service");

        // Assert that the export request's resource UUID is correct.
        Assertions.assertEquals(ExportEventTestHelper.EXPORT_CE_RESOURCE_UUID, capturedResourceUuid.getValue(), "unexpected resource UUID sent to the export service");

        // Load the expected body output.
        final URL jsonResourceUrl = this.getClass().getResource("/resultstransformers/event/expectedResult.json");
        Assertions.assertNotNull(jsonResourceUrl, "the JSON file with the expected result was not located");

        final String expectedContents = Files.readString(Path.of(jsonResourceUrl.toURI()));

        // Assert that both the expected contents and the result are valid JSON
        // objects.
        final JsonArray expectedJson = new JsonArray(expectedContents);
        final JsonArray resultJson = new JsonArray(capturedJSONContents.getValue());

        // Encode both prettily so that if an error occurs, it is easier to
        // spot where the problem is.
        Assertions.assertEquals(expectedJson.encodePrettily(), resultJson.encodePrettily(), "unexpected JSON body received");

        // Assert that the successes counter was incremented, and that the
        // failures counter did not increment.
        this.micrometerAssertionHelper.assertCounterIncrement(ExportEventListener.EXPORTS_SERVICE_FAILURES_COUNTER, 0);
        this.micrometerAssertionHelper.assertCounterIncrement(ExportEventListener.EXPORTS_SERVICE_SUCCESSES_COUNTER, 1);
    }

    /**
     * Tests that when a valid CSV export request for exporting events is
     * received, then a valid request is sent to the export service, containing
     * the expected body.
     */
    @Test
    void testExportEventsCSV() throws IOException, URISyntaxException {
        // Save the counter values to assert the "successes count" change later.
        // Also save the "errors" counter to assert that it did not
        // accidentally increment.
        this.micrometerAssertionHelper.saveCounterValuesBeforeTest(ExportEventListener.EXPORTS_SERVICE_FAILURES_COUNTER);
        this.micrometerAssertionHelper.saveCounterValuesBeforeTest(ExportEventListener.EXPORTS_SERVICE_SUCCESSES_COUNTER);

        final InMemorySource<String> exportIn = this.inMemoryConnector.source(EXPORT_CHANNEL);

        // Generate an export request but set a resource type which we don't
        // support.
        final GenericConsoleCloudEvent<ResourceRequest> cee = ExportEventTestHelper.createExportCloudEventFixture(Format.JSON);
        final ResourceRequestClass data = cee.getData().getResourceRequest();
        data.setFormat(Format.CSV);

        // Serialize the payload and send it to the Kafka topic.
        final ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();

        // Return fixture events when the repository is called.
        Mockito.when(this.eventRepository.findEventsToExport(Mockito.eq(DEFAULT_ORG_ID), Mockito.any(), Mockito.any())).thenReturn(TransformersHelpers.getFixtureEvents());

        // Send the JSON payload.
        exportIn.send(consoleCloudEventParser.toJson(cee));

        // Assert that the export service was called as expected.
        final ArgumentCaptor<UUID> capturedExportUuid = ArgumentCaptor.forClass(UUID.class);
        final ArgumentCaptor<String> capturedApplication = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<UUID> capturedResourceUuid = ArgumentCaptor.forClass(UUID.class);
        final ArgumentCaptor<String> capturedCSVContents = ArgumentCaptor.forClass(String.class);

        // Wait at most 10 seconds before failing.
        Mockito.verify(this.exportService, Mockito.timeout(10000).times(1)).uploadCSVExport(capturedExportUuid.capture(), capturedApplication.capture(), capturedResourceUuid.capture(), capturedCSVContents.capture());

        // Assert that the export request's UUID is correct.
        Assertions.assertEquals(ExportEventTestHelper.EXPORT_CE_EXPORT_UUID, capturedExportUuid.getValue(), "unexpected export request UUID sent to the export service");

        // Assert that the export request's application is correct.
        Assertions.assertEquals(URLEncoder.encode(ExportEventListener.APPLICATION_NAME, UTF_8), capturedApplication.getValue(), "unexpected application's name sent to the export service");

        // Assert that the export request's resource UUID is correct.
        Assertions.assertEquals(ExportEventTestHelper.EXPORT_CE_RESOURCE_UUID, capturedResourceUuid.getValue(), "unexpected resource UUID sent to the export service");

        // Load the expected body output.
        final URL csvResourceUrl = this.getClass().getResource("/resultstransformers/event/expectedResult.csv");
        Assertions.assertNotNull(csvResourceUrl, "the CSV file with the expected result was not located");

        final String expectedContents = Files.readString(Path.of(csvResourceUrl.toURI()));

        Assertions.assertEquals(expectedContents, capturedCSVContents.getValue(), "unexpected CSV body received");

        // Assert that the successes counter was incremented, and that the
        // failures counter did not increment.
        this.micrometerAssertionHelper.assertCounterIncrement(ExportEventListener.EXPORTS_SERVICE_FAILURES_COUNTER, 0);
        this.micrometerAssertionHelper.assertCounterIncrement(ExportEventListener.EXPORTS_SERVICE_SUCCESSES_COUNTER, 1);
    }

    /**
     * Asserts that the sent error notification to the export service contains
     * the proper export request's UUID, the proper application name, the
     * proper resource request's UUID and the proper status code and proper
     * error messages.
     * @param expectedStatusCode the expected status code to check.
     * @param expectedErrorMessage the expected error message to check.
     */
    void assertErrorNotificationIsCorrect(final int expectedStatusCode, final String expectedErrorMessage) {
        // Assert that the export service was called as expected.
        final ArgumentCaptor<UUID> capturedExportUuid = ArgumentCaptor.forClass(UUID.class);
        final ArgumentCaptor<String> capturedApplication = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<UUID> capturedResourceUuid = ArgumentCaptor.forClass(UUID.class);
        final ArgumentCaptor<ExportError> capturedError = ArgumentCaptor.forClass(ExportError.class);

        // Wait at most 10 seconds before failing.
        Mockito.verify(this.exportService, Mockito.timeout(10000).times(1)).notifyErrorExport(capturedExportUuid.capture(), capturedApplication.capture(), capturedResourceUuid.capture(), capturedError.capture());

        // Assert that the export request's UUID is correct.
        Assertions.assertEquals(ExportEventTestHelper.EXPORT_CE_EXPORT_UUID, capturedExportUuid.getValue(), "unexpected export request UUID sent to the export service");

        // Assert that the export request's application is correct.
        Assertions.assertEquals(ExportEventListener.APPLICATION_NAME, capturedApplication.getValue(), "unexpected application's name sent to the export service");

        // Assert that the export request's resource UUID is correct.
        Assertions.assertEquals(ExportEventTestHelper.EXPORT_CE_RESOURCE_UUID, capturedResourceUuid.getValue(), "unexpected resource UUID sent to the export service");

        // Assert that the sent error has the expected payload.
        final ExportError capturedPayload = capturedError.getValue();

        Assertions.assertEquals(expectedStatusCode, capturedPayload.error(), "unexpected status code sent to the export service");
        Assertions.assertEquals(expectedErrorMessage, capturedPayload.message(), "unexpected error message sent to the export service");
    }
}
