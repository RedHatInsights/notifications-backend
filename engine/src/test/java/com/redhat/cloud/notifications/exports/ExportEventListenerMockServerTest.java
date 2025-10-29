package com.redhat.cloud.notifications.exports;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequest;
import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.event.parser.GenericConsoleCloudEvent;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.exports.transformers.TransformersHelpers;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.exports.ExportEventListener.EXPORT_CHANNEL;
import static org.awaitility.Awaitility.await;

/**
 * While {@link ExportEventListenerTest} uses mocks to test the whole logic of
 * the handler, this class uses a Mock Server in order to check some other
 * things that are just simpler to check by using a mock server.
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ExportEventListenerMockServerTest {
    @InjectMock
    EventRepository eventRepository;

    @Any
    @Inject
    InMemoryConnector inMemoryConnector;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    /**
     * Sets up the routes for the Mock Server.
     */
    @BeforeEach
    void setUpMockServerRoutes() {
        final WireMockServer mockServer = MockServerLifecycleManager.getClient();

        mockServer.stubFor(
            post(urlPathMatching(".*/upload"))
                .willReturn(aResponse()
                    .withStatus(200))
        );
    }

    /**
     * Clears everything from the Mock Server.
     */
    @AfterEach
    void clearMockServer() {
        MockServerLifecycleManager.getClient().resetAll();
    }

    /**
     * Tests that when the events are requested in the different formats, the
     * requests sent to the export service have the right "Content-Type" header
     * value.
     */
    @Test
    void testContentTypeHeaders() {
        final InMemorySource<String> exportIn = this.inMemoryConnector.source(EXPORT_CHANNEL);

        // Set up a simple helper class.
        record TestCase(GenericConsoleCloudEvent<ResourceRequest> cloudEvent, String expectedMediaType) { }

        final List<TestCase> testCases = new ArrayList<>();

        // Test that when requesting events in a CSV format, the content type
        // is "text/csv".
        testCases.add(
            new TestCase(
                ExportEventTestHelper.createExportCloudEventFixture(Format.CSV),
                "text/csv"
            )
        );

        // Test that when requesting events in a JSON format, the content type
        // is "application/json".
        testCases.add(
            new TestCase(
                ExportEventTestHelper.createExportCloudEventFixture(Format.JSON),
                "application/json"
            )
        );

        for (final TestCase testCase : testCases) {
            this.setUpMockServerRoutes();

            // Serialize the payload and send it to the Kafka topic.
            final ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();

            // Return fixture events when the repository is called.
            Mockito.when(this.eventRepository.findEventsToExport(Mockito.eq(DEFAULT_ORG_ID), Mockito.any(), Mockito.any())).thenReturn(TransformersHelpers.getFixtureEvents());

            // Send the JSON payload.
            exportIn.send(consoleCloudEventParser.toJson(testCase.cloudEvent()));

            // Wait until the handler sends an error to the export service.
            await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> !MockServerLifecycleManager.getClient().getAllServeEvents().isEmpty());

            // Assert that only one request was received.
            final List<ServeEvent> serveEvents = MockServerLifecycleManager.getClient().getAllServeEvents();
            Assertions.assertEquals(1, serveEvents.size(), "unexpected number of requests received in the upload endpoint");

            final Request request = serveEvents.get(0).getRequest();

            // Assert that the "Content-Type" header contains the expected
            // value.
            Assertions.assertEquals(
                request.getHeader("Content-Type"),
                testCase.expectedMediaType(),
                "unexpected content type header sent to the export service"
            );

            // Clear the Mock Server from any requests to not mess up with the
            // next iteration.
            this.clearMockServer();
        }
    }

    /**
     * Tests that when a "client error" status code is returned from the export
     * service then the failures counter increases.
     */
    @Test
    void testError400IncreasesFailureMetric() {
        final InMemorySource<String> exportIn = this.inMemoryConnector.source(EXPORT_CHANNEL);

        // Save all the counters to later assert that only the expected ones
        // were increased.
        this.micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(
            ExportEventListener.EXPORTS_SERVICE_FAILURES_COUNTER,
            ExportEventListener.FAILURE_KEY,
            ExportEventListener.FAILURE_CLIENT_ERROR
        );
        this.micrometerAssertionHelper.saveCounterValuesBeforeTest(ExportEventListener.EXPORTS_SERVICE_SUCCESSES_COUNTER);

        // Generate the expected event.
        final GenericConsoleCloudEvent<ResourceRequest> cce = ExportEventTestHelper.createExportCloudEventFixture(Format.JSON);

        // Serialize the payload and send it to the Kafka topic.
        final ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();

        // Return fixture events when the repository is called.
        Mockito.when(this.eventRepository.findEventsToExport(Mockito.eq(DEFAULT_ORG_ID), Mockito.any(), Mockito.any())).thenReturn(TransformersHelpers.getFixtureEvents());

        // Reset the mock server since we need it to return a specific response.
        MockServerLifecycleManager.getClient().resetAll();
        MockServerLifecycleManager.getClient().stubFor(
            post(urlPathMatching(".*/upload"))
                .willReturn(aResponse()
                    .withStatus(HttpStatus.SC_BAD_REQUEST))
        );

        // Send the JSON payload.
        exportIn.send(consoleCloudEventParser.toJson(cce));

        // Assert that only the failures counter increased their value.
        this.micrometerAssertionHelper.awaitAndAssertCounterIncrementFilteredByTags(
            ExportEventListener.EXPORTS_SERVICE_FAILURES_COUNTER,
            ExportEventListener.FAILURE_KEY,
            ExportEventListener.FAILURE_CLIENT_ERROR,
            1);
        this.micrometerAssertionHelper.assertCounterIncrement(ExportEventListener.EXPORTS_SERVICE_SUCCESSES_COUNTER, 0);
    }

    /**
     * Tests that when a "server error" status code is returned from the export
     * service then the failures counter increases.
     */
    @Test
    void testError500IncreasesFailureMetric() {
        final InMemorySource<String> exportIn = this.inMemoryConnector.source(EXPORT_CHANNEL);

        // Save all the counters to later assert that only the expected ones
        // were increased.
        this.micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(
            ExportEventListener.EXPORTS_SERVICE_FAILURES_COUNTER,
            ExportEventListener.FAILURE_KEY,
            ExportEventListener.FAILURE_SERVER_ERROR
        );
        this.micrometerAssertionHelper.saveCounterValuesBeforeTest(ExportEventListener.EXPORTS_SERVICE_SUCCESSES_COUNTER);

        // Generate the expected event.
        final GenericConsoleCloudEvent<ResourceRequest> cce = ExportEventTestHelper.createExportCloudEventFixture(Format.JSON);

        // Serialize the payload and send it to the Kafka topic.
        final ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();

        // Return fixture events when the repository is called.
        Mockito.when(this.eventRepository.findEventsToExport(Mockito.eq(DEFAULT_ORG_ID), Mockito.any(), Mockito.any())).thenReturn(TransformersHelpers.getFixtureEvents());

        // Reset the mock server since we need it to return a specific response.
        MockServerLifecycleManager.getClient().resetAll();
        MockServerLifecycleManager.getClient().stubFor(
            post(urlPathMatching(".*/upload"))
                .willReturn(aResponse()
                    .withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR))
        );

        // Send the JSON payload.
        exportIn.send(consoleCloudEventParser.toJson(cce));

        // Wait until the handler sends the payload to the export service.
        await()
            .atMost(Duration.ofSeconds(10))
            .until(() -> !MockServerLifecycleManager.getClient().getAllServeEvents().isEmpty());

        // Assert that only the failures counter increased their value.
        this.micrometerAssertionHelper.awaitAndAssertCounterIncrementFilteredByTags(
            ExportEventListener.EXPORTS_SERVICE_FAILURES_COUNTER,
            ExportEventListener.FAILURE_KEY,
            ExportEventListener.FAILURE_SERVER_ERROR,
            1);
        this.micrometerAssertionHelper.assertCounterIncrement(ExportEventListener.EXPORTS_SERVICE_SUCCESSES_COUNTER, 0);
    }
}
