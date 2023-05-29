package com.redhat.cloud.notifications.exports;

import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequest;
import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.event.parser.GenericConsoleCloudEvent;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.exports.transformers.TransformersHelpers;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;
import io.smallrye.reactive.messaging.providers.connectors.InMemorySource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.MediaType;

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.exports.ExportEventListener.EXPORT_CHANNEL;
import static org.awaitility.Awaitility.await;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * While {@link ExportEventListenerTest} uses mocks to test the whole logic of
 * the handler, this class uses a Mock Server in order to explicitly check that
 * the content type headers we send are the correct ones.
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ExportServiceHeadersTest {
    @InjectMock
    EventRepository eventRepository;

    @Any
    @Inject
    InMemoryConnector inMemoryConnector;

    /**
     * Sets up the routes for the Mock Server.
     */
    @BeforeEach
    void setUpMockServerRoutes() {
        final ClientAndServer mockServer = MockServerLifecycleManager.getClient();

        mockServer
            .when(request().withPath(".*/upload"))
            .respond(response().withStatusCode(200));
    }

    /**
     * Clears everything from the Mock Server.
     */
    @AfterEach
    void clearMockServer() {
        MockServerLifecycleManager.getClient().reset();
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
                MediaType.APPLICATION_JSON.toString()
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
                .until(() -> MockServerLifecycleManager.getClient().retrieveRecordedRequests(request().withPath(".*/upload")).length != 0);

            // Assert that only one request was received.
            final HttpRequest[] requests = MockServerLifecycleManager.getClient().retrieveRecordedRequests(request().withPath(".*/upload"));
            Assertions.assertEquals(1, requests.length, "unexpected number of requests received in the upload endpoint");

            final HttpRequest request = requests[0];

            // Assert that the "Content-Type" header contains the expected
            // value.
            Assertions.assertEquals(
                request.getFirstHeader("Content-Type"),
                testCase.expectedMediaType(),
                "unexpected content type header sent to the export service"
            );

            // Clear the Mock Server from any requests to not mess up with the
            // next iteration.
            this.clearMockServer();
        }
    }
}
