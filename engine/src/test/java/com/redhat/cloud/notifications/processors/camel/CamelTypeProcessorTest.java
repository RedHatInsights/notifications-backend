package com.redhat.cloud.notifications.processors.camel;

import com.redhat.cloud.notifications.Base64Utils;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.converters.MapConverter;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.openbridge.Bridge;
import com.redhat.cloud.notifications.openbridge.BridgeHelper;
import com.redhat.cloud.notifications.openbridge.BridgeItemList;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.quarkus.logging.Log;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.reactive.messaging.TracingMetadata;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;
import io.smallrye.reactive.messaging.providers.connectors.InMemorySink;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.http.HttpStatus;
import org.apache.kafka.common.header.Headers;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.model.ClearType;
import org.mockserver.model.HttpRequest;

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import javax.ws.rs.HttpMethod;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_HEADER;
import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor.CAMEL_SUBTYPE_HEADER;
import static com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor.CLOUD_EVENT_ACCOUNT_EXTENSION_KEY;
import static com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor.CLOUD_EVENT_ORG_ID_EXTENSION_KEY;
import static com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor.CLOUD_EVENT_TYPE_PREFIX;
import static com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor.NOTIF_METADATA_KEY;
import static com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor.PROCESSED_COUNTER_NAME;
import static com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor.TOCAMEL_CHANNEL;
import static com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor.TOKEN_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class CamelTypeProcessorTest {

    public static final String SUB_TYPE_KEY = "subType";
    public static final String SUB_TYPE = "sub-type";

    /**
     * Event fixtures for the {@link #buildEvent()} function.
     */
    private static final UUID FIXTURE_EVENT_ORIGINAL_UUID = UUID.randomUUID();
    private static final String FIXTURE_ACTION_ORG_ID = "test-event-org-id";

    /**
     * Action fixtures for the {@link #buildEvent()} function.
     */
    private static final String FIXTURE_ACTION_APP = "app";
    private static final String FIXTURE_ACTION_VERSION = "v1.0.0";
    private static final String FIXTURE_ACTION_BUNDLE = "bundle";
    private static final String FIXTURE_ACTION_EVENT_TYPE = "event-type";
    private static final LocalDateTime FIXTURE_ACTION_TIMESTAMP = LocalDateTime.now();
    private static final String FIXTURE_ACTION_ACCOUNT_ID = "account-id";
    private static final List<Recipient> FIXTURE_ACTION_RECIPIENTS = List.of();
    private static final String FIXTURE_ACTION_CONTEXT = "context-key";
    private static final String FIXTURE_ACTION_CONTEXT_VALUE = "context-value";
    private static final String FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY = "k1";
    private static final String FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_VALUE = "v1";
    private static final String FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_2 = "k2";
    private static final String FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_2_VALUE = "v2";
    private static final String FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_3 = "k3";
    private static final String FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_3_VALUE = "v3";

    /**
     * Camel endpoint fixtures for the {@link #buildCamelEndpoint(String)} function.
     */
    private static final String FIXTURE_CAMEL_URL = "https://redhat.com";
    private static final Boolean FIXTURE_CAMEL_SSL_VERIFICATION = true;
    private static final String FIXTURE_CAMEL_SECRET_TOKEN = "top-secret";
    private static final String FIXTURE_CAMEL_BASIC_AUTH_USERNAME = "john";
    private static final String FIXTURE_CAMEL_BASIC_AUTH_PASSWORD = "doe";
    private static final String FIXTURE_CAMEL_EXTRAS_PROCESSOR_NAME = "test-custom-processor-name";

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @Inject
    CamelTypeProcessor processor;

    @Inject
    BridgeHelper bridgeHelper;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    ResourceHelpers resourceHelpers;

    @InjectMock
    NotificationHistoryRepository notificationHistoryRepository;

    /**
     * Used to test that the environment's URL is properly set when sending events to RHOSE.
     */
    @Inject
    Environment environment;

    @BeforeEach
    void beforeEach() {
        micrometerAssertionHelper.saveCounterValueWithTagsBeforeTest(PROCESSED_COUNTER_NAME, SUB_TYPE_KEY);
        processor.reset();
    }

    @AfterEach
    void afterEach() {
        micrometerAssertionHelper.clearSavedValues();
    }

    @Test
    void testCamelEndpointProcessing() {

        // We need input data for the test.
        Event event = buildEvent();
        Endpoint endpoint1 = buildCamelEndpoint(event.getAction().getAccountId());
        CamelProperties properties1 = endpoint1.getProperties(CamelProperties.class);
        Endpoint endpoint2 = buildCamelEndpoint(event.getAction().getAccountId());

        // Let's trigger the processing.
        processor.process(event, List.of(endpoint1, endpoint2));
        ArgumentCaptor<NotificationHistory> historyArgumentCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(notificationHistoryRepository, times(2)).createNotificationHistory(historyArgumentCaptor.capture());
        List<NotificationHistory> result = historyArgumentCaptor.getAllValues();

        // Two endpoints should have been processed.
        assertEquals(2, result.size());
        // Metrics should report the same thing.
        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_COUNTER_NAME, 2, SUB_TYPE_KEY, SUB_TYPE);
        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_COUNTER_NAME, 0, SUB_TYPE_KEY, "other-type");
        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_COUNTER_NAME, 0);

        // Let's have a look at the first result entry fields.
        assertEquals(event, result.get(0).getEvent());
        assertEquals(endpoint1, result.get(0).getEndpoint());
        assertEquals(CAMEL, result.get(0).getEndpointType());
        assertNotNull(result.get(0).getInvocationTime());
        // The invocation will be complete when the response from Camel has been received.
        assertFalse(result.get(0).isInvocationResult());
        assertEquals(NotificationStatus.PROCESSING, result.get(0).getStatus());

        // Now let's check the Kafka messages sent to the outgoing channel.
        InMemorySink<String> inMemorySink = inMemoryConnector.sink(TOCAMEL_CHANNEL);
        // The channel should have received two messages.
        assertEquals(2, inMemorySink.received().size());

        // We'll only check the payload and metadata of the first Kafka message.
        Message<String> message = inMemorySink.received().get(0);

        // The payload should contain the action events.
        JsonObject payload = new JsonObject(message.getPayload());
        assertNotNull(payload.getJsonArray("events").getJsonObject(0).getString("payload"));

        // The processor added a 'notif-metadata' field to the payload, let's have a look at it.
        JsonObject notifMetadata = payload.getJsonObject(NOTIF_METADATA_KEY);
        assertEquals(properties1.getDisableSslVerification().toString(), notifMetadata.getString("trustAll"));
        assertEquals(properties1.getUrl(), notifMetadata.getString("url"));
        assertEquals(endpoint1.getSubType(), notifMetadata.getString("type"));

        assertEquals(new MapConverter().convertToDatabaseColumn(properties1.getExtras()), notifMetadata.getString("extras"));
        assertEquals(properties1.getSecretToken(), notifMetadata.getString(TOKEN_HEADER));
        checkBasicAuthentication(notifMetadata, properties1.getBasicAuthentication());

        // Finally, we need to check the Kafka message metadata.
        UUID historyId = result.get(0).getId();
        checkKafkaMetadata(message, historyId, endpoint1.getSubType());
        checkCloudEventMetadata(message, historyId, endpoint1.getAccountId(), endpoint1.getOrgId(), endpoint1.getSubType());
        checkTracingMetadata(message);

        // DB and Kafka data must be cleared to prevent a side effect on other tests.
        inMemorySink.clear();
        resourceHelpers.deleteEndpoint(endpoint1.getId());
        resourceHelpers.deleteEndpoint(endpoint2.getId());
    }

    @Test
    void oBEndpointProcessingBadBridge() {

        // We need input data for the test.
        Event event = buildEvent();
        event.setAccountId("rhid123");
        event.setOrgId(DEFAULT_ORG_ID);
        Endpoint endpoint = buildCamelEndpoint(event.getAction().getAccountId());
        endpoint.setSubType("slack");

        featureFlipper.setObEnabled(true);
        bridgeHelper.setOurBridgeName(null);

        // Let's trigger the processing.
        // First with 'random OB endpoints', so we expect this to fail
        processor.process(event, List.of(endpoint));
        ArgumentCaptor<NotificationHistory> historyArgumentCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(notificationHistoryRepository, times(1)).createNotificationHistory(historyArgumentCaptor.capture());
        List<NotificationHistory> result = historyArgumentCaptor.getAllValues();

        // One endpoint should have been processed.
        assertEquals(1, result.size());
        // Metrics should report the same thing.
        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_COUNTER_NAME, 0, SUB_TYPE_KEY, SUB_TYPE);
        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_COUNTER_NAME, 1, SUB_TYPE_KEY, "slack");
        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_COUNTER_NAME, 0, SUB_TYPE_KEY, "other-type");
        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_COUNTER_NAME, 0);

        // Let's have a look at the first result entry fields.
        NotificationHistory historyItem = result.get(0);

        assertEquals(event, historyItem.getEvent());
        assertEquals(endpoint, historyItem.getEndpoint());
        assertEquals(CAMEL, historyItem.getEndpointType());
        assertEquals("slack", historyItem.getEndpointSubType());
        assertNotNull(historyItem.getDetails());
        assertEquals(1, historyItem.getDetails().size());
        Map<String, Object> details = historyItem.getDetails();
        assertTrue(details.containsKey("failure"));

        featureFlipper.setObEnabled(false);
    }

    @Test
    void oBEndpointProcessingGoodBridge() {

        featureFlipper.setObEnabled(true);

        // We need input data for the test.
        Event event = buildEvent();
        event.setAccountId("rhid123");
        event.setOrgId(DEFAULT_ORG_ID);
        Endpoint endpoint = buildCamelEndpoint(event.getAction().getAccountId());
        endpoint.setSubType("slack");

        // Set up some mock OB endpoints (simulate valid bridge)
        String eventsEndpoint = getMockServerUrl() + "/events";
        System.out.println("==> Setting events endpoint to " + eventsEndpoint);
        Bridge bridge = new Bridge("321", eventsEndpoint, "my bridge");
        List<Bridge> items = new ArrayList<>();
        items.add(bridge);
        BridgeItemList<Bridge> bridgeList = new BridgeItemList<>();
        bridgeList.setItems(items);
        bridgeList.setSize(1);
        bridgeList.setTotal(1);
        Map<String, String> auth = new HashMap<>();
        auth.put("access_token", "li-la-lu-token");
        Map<String, String> obProcessor = new HashMap<>();
        obProcessor.put("id", "p-my-id");

        MockServerConfig.addOpenBridgeEndpoints(auth, bridgeList);
        bridgeHelper.setOurBridgeName("my bridge");

        System.out.println("==> Auth token " + bridgeHelper.getAuthToken());
        System.out.println("==> The bridge " + bridgeHelper.getBridgeIfNeeded());

        // Process again
        reset(notificationHistoryRepository); // TODO Using reset is bad, split this test instead
        processor.process(event, List.of(endpoint));
        ArgumentCaptor<NotificationHistory> historyArgumentCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(notificationHistoryRepository, times(1)).createNotificationHistory(historyArgumentCaptor.capture());
        List<NotificationHistory> result = historyArgumentCaptor.getAllValues();

        // One endpoint should have been processed.
        assertEquals(1, result.size());
        // Metrics should report the same thing.
        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_COUNTER_NAME, 1, SUB_TYPE_KEY, "slack");

        // Let's have a look at the first result entry fields.
        NotificationHistory historyItem = result.get(0);
        assertEquals(event, historyItem.getEvent());
        assertEquals(endpoint, historyItem.getEndpoint());
        assertEquals(CAMEL, historyItem.getEndpointType());
        assertEquals("slack", historyItem.getEndpointSubType());
        assertNull(historyItem.getDetails());

        // Now try again, but the remote throws an error
        event.getAction().setAccountId("something-random");
        event.getAction().setOrgId(DEFAULT_ORG_ID);
        reset(notificationHistoryRepository); // TODO Using reset is bad, split this test instead
        processor.process(event, List.of(endpoint));
        historyArgumentCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(notificationHistoryRepository, times(1)).createNotificationHistory(historyArgumentCaptor.capture());
        result = historyArgumentCaptor.getAllValues();
        assertEquals(1, result.size());
        // Metrics should report the same thing.
        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_COUNTER_NAME, 2, SUB_TYPE_KEY, "slack");

        // Let's have a look at the first result entry fields.
        historyItem = result.get(0);
        assertEquals(event, historyItem.getEvent());
        assertEquals(1, historyItem.getDetails().size());
        Map<String, Object> details = historyItem.getDetails();
        assertTrue(details.containsKey("failure"));

        assertNotNull(historyItem.getInvocationTime());
        // The invocation will be complete when the response from Camel has been received.
        assertFalse(historyItem.isInvocationResult());
        assertEquals(NotificationStatus.FAILED_INTERNAL, result.get(0).getStatus());

        MockServerConfig.clearOpenBridgeEndpoints(bridge);
        featureFlipper.setObEnabled(false);

    }

    /**
     * Tests that the payload sent to RHOSE is the one that is expected.
     */
    @Test
    void callOpenBridgeExpectedJson() {
        // Set "RHOSE" feature enabled to be able to test this.
        this.featureFlipper.setObEnabled(true);

        // The path that will be set up in the Mock Server, and that will be used as the Bridge's endpoint.
        final String testMockServerPath = "/events/slack/json-output-check";

        // Create a request expectation for when the RHOSE sends the request. It is created separately because
        // it is needed later to retrieve the requests that match with this expectation.
        final HttpRequest expectedRequest = HttpRequest.request().withPath(testMockServerPath).withMethod(HttpMethod.POST);
        MockServerLifecycleManager.getClient().when(expectedRequest).respond(
            response().withStatusCode(HttpStatus.SC_NO_CONTENT)
        );

        // Create the input data for the test.
        Event event = buildEvent();
        event.setAccountId("rhid123");
        event.setOrgId(DEFAULT_ORG_ID);

        Endpoint endpoint = buildCamelEndpoint(event.getAction().getAccountId());
        endpoint.setSubType("slack");

        // Set up some mock RHOSE endpoints (simulate a valid bridge)
        final String eventsEndpoint = MockServerLifecycleManager.getMockServerUrl() + testMockServerPath;
        Log.infof("The event's endpoint is set to %s", eventsEndpoint);

        // Create the bridge.
        final Bridge bridge = new Bridge("321", eventsEndpoint, "my bridge");

        List<Bridge> items = new ArrayList<>();
        items.add(bridge);

        // Create the Bridge list for the Mock Server Configuration.
        BridgeItemList<Bridge> bridgeList = new BridgeItemList<>();
        bridgeList.setItems(items);
        bridgeList.setSize(1);
        bridgeList.setTotal(1);

        // Create an authentication token for the Bridge.
        Map<String, String> auth = new HashMap<>();
        auth.put("access_token", "li-la-lu-token");

        // Add the Bridge and add a name for it.
        MockServerConfig.addOpenBridgeEndpoints(auth, bridgeList);
        this.bridgeHelper.setOurBridgeName("my bridge");

        Log.infof("Authentication token for the bridge helper: %s", this.bridgeHelper.getAuthToken());
        Log.infof("Used bridge for the test: %s", this.bridgeHelper.getBridgeIfNeeded());

        // Call the function under test.
        this.processor.process(event, List.of(endpoint));

        // Get the recorded requests from the Mock Server.
        final HttpRequest[] recordedRequests = MockServerLifecycleManager.getClient().retrieveRecordedRequests(expectedRequest);

        // We are expecting one call to the Bridge Event Service, so anything different from that should be noted as an
        // error.
        if (recordedRequests.length != 1) {
            Assertions.fail(String.format("the number of recorded requests doesn't match the expected one. Want %d, got %d", 1, recordedRequests.length));
        }

        // Get the request's JSON body.
        final HttpRequest req = recordedRequests[0];
        final JsonObject json = new JsonObject(req.getBodyAsString());

        // Assert the results for the top level fields.
        Assertions.assertTrue(json.containsKey("rhorgid"), "the expected \"rhorgid\" field is not present");
        Assertions.assertTrue(json.containsKey("specversion"), "the expected \"specversion\" field is not present");
        Assertions.assertTrue(json.containsKey("id"), "the expected \"id\" field is not present");
        Assertions.assertTrue(json.containsKey("source"), "the expected \"source|\" field is not present");
        Assertions.assertTrue(json.containsKey("processorname"), "the expected \"processorname\" field is not present");
        Assertions.assertTrue(json.containsKey("type"), "the expected \"type\" field is not present");
        Assertions.assertTrue(json.containsKey("originaleventid"), "the expected \"originaleventid\" field is not present");
        Assertions.assertTrue(json.containsKey("environment_url"), "the expected \"environment_url\" key is not present");
        Assertions.assertTrue(json.containsKey("data"), "the expected \"data\" field is not present");
        Assertions.assertEquals(this.environment.url(), json.getString("environment_url"), "the environment URL isn't the same");

        // Assert the values for the top level fields.
        Assertions.assertEquals(DEFAULT_ORG_ID, json.getString("rhorgid"), "the \"rhorgid\" values don't match");
        Assertions.assertEquals(CamelTypeProcessor.SPEC_VERSION, json.getString("specversion"), "the \"specversion\" values don't match");
        // The UUID is randomly generated, so the only way to test that the ID is valid is to check if it is a valid
        // UUID.
        UUID.fromString(json.getString("id"));
        Assertions.assertEquals(CamelTypeProcessor.SOURCE, json.getString("source"), "the \"source\" values don't match");
        Assertions.assertEquals(FIXTURE_CAMEL_EXTRAS_PROCESSOR_NAME, json.getString("processorname"), "the \"processorname\" values don't match");
        Assertions.assertEquals(CamelTypeProcessor.TYPE, json.getString("type"), "the \"type\" values don't match");
        Assertions.assertEquals(this.environment.url(), json.getString("environment_url"), "the \"environment_url\" values don't match");
        Assertions.assertEquals(FIXTURE_EVENT_ORIGINAL_UUID.toString(), json.getString("originaleventid"), "the \"originaleventid\" values don't match");

        // Assert the results for the fields in the "data" object.
        final JsonObject data = json.getJsonObject("data");
        Assertions.assertTrue(data.containsKey("account_id"), "the \"account_id\" field is not present");
        Assertions.assertTrue(data.containsKey("application"), "the \"application\" field is not present");
        Assertions.assertTrue(data.containsKey("bundle"), "the \"bundle\" field is not present");
        Assertions.assertTrue(data.containsKey("context"), "the \"context\" field is not present");
        Assertions.assertTrue(data.containsKey("event_type"), "the \"event_type\" field is not present");
        Assertions.assertTrue(data.containsKey("events"), "the \"events\" field is not present");
        Assertions.assertTrue(data.containsKey("org_id"), "the \"org_id\" field is not present");
        Assertions.assertTrue(data.containsKey("timestamp"), "the \"timestamp\" field is not present");

        // Assert the values for the fields in the "data" object.
        Assertions.assertEquals(FIXTURE_ACTION_ACCOUNT_ID, data.getString("account_id"), "the \"account_id\" values don't match");
        Assertions.assertEquals(FIXTURE_ACTION_APP, data.getString("application"), "the \"application\" values don't match");
        Assertions.assertEquals(FIXTURE_ACTION_BUNDLE, data.getString("bundle"), "the \"bundle\" values don't match");
        Assertions.assertEquals(FIXTURE_ACTION_EVENT_TYPE, data.getString("event_type"), "the \"event_type\" values don't match");
        Assertions.assertEquals(FIXTURE_ACTION_ORG_ID, data.getString("org_id"), "the \"org_id\" values don't match");
        Assertions.assertEquals(FIXTURE_ACTION_TIMESTAMP.toString(), data.getString("timestamp"), "the \"timestamp\" values don't match");

        // Assert the fields and the values in the "context" object.
        final JsonObject context = data.getJsonObject("context");
        Assertions.assertTrue(context.containsKey(FIXTURE_ACTION_CONTEXT), String.format("the expected \"%s\" context field is not present", FIXTURE_ACTION_CONTEXT));
        Assertions.assertEquals(FIXTURE_ACTION_CONTEXT_VALUE, context.getString(FIXTURE_ACTION_CONTEXT), String.format("the \"%s\" context values don't match", FIXTURE_ACTION_CONTEXT_VALUE));

        // Assert the "events" array.
        final JsonArray events = data.getJsonArray("events");
        if (events.size() != 1) {
            Assertions.fail(String.format("the events array has an unexpected number of elements. Want %s, got %s", 1, events.size()));
        }

        final JsonObject eventResult = events.getJsonObject(0);

        Assertions.assertTrue(eventResult.containsKey("metadata"), "the \"metadata\" field is not present");
        Assertions.assertTrue(eventResult.containsKey("payload"), "the \"payload\" field is not present");

        // Assert the "payload" object in the event.
        final JsonObject payload = eventResult.getJsonObject("payload");
        Assertions.assertTrue(payload.containsKey(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY), String.format("the \"%s\" payload field is not present", FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY));
        Assertions.assertTrue(payload.containsKey(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_2), String.format("the \"%s\" payload field is not present", FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_2));
        Assertions.assertTrue(payload.containsKey(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_3), String.format("the \"%s\" payload field is not present", FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_3));

        Assertions.assertEquals(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_VALUE, payload.getString(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY), "the payload's additional property value doesn't match");
        Assertions.assertEquals(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_2_VALUE, payload.getString(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_2), "the payload's additional property value doesn't match");
        Assertions.assertEquals(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_3_VALUE, payload.getString(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_3), "the payload's additional property value doesn't match");

        // Clear the path from the Mock Server since this won't be used by any other test.
        MockServerLifecycleManager.getClient().clear(request().withPath(testMockServerPath), ClearType.ALL);

        // Clear also the RHOSE endpoints for this test.
        MockServerConfig.clearOpenBridgeEndpoints(bridge);

        // Reset the feature flag to false in order to avoid affecting any other tests.
        featureFlipper.setObEnabled(false);
    }

    private static Event buildEvent() {
        Action action = new Action();
        action.setVersion(FIXTURE_ACTION_VERSION);
        action.setBundle(FIXTURE_ACTION_BUNDLE);
        action.setApplication(FIXTURE_ACTION_APP);
        action.setEventType(FIXTURE_ACTION_EVENT_TYPE);
        action.setTimestamp(FIXTURE_ACTION_TIMESTAMP);
        action.setAccountId(FIXTURE_ACTION_ACCOUNT_ID);
        action.setOrgId(FIXTURE_ACTION_ORG_ID);
        action.setRecipients(FIXTURE_ACTION_RECIPIENTS);

        Context context = new Context.ContextBuilder().build();
        context.setAdditionalProperty(FIXTURE_ACTION_CONTEXT, FIXTURE_ACTION_CONTEXT_VALUE);
        action.setContext(context);

        action.setEvents(
            List.of(
                new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                    .withMetadata(new Metadata.MetadataBuilder().build())
                    .withPayload(
                        new Payload.PayloadBuilder()
                            .withAdditionalProperty(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY, FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_VALUE)
                            .withAdditionalProperty(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_2, FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_2_VALUE)
                            .withAdditionalProperty(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_3, FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_3_VALUE)
                            .build()
                    )
                .build()
            )
        );

        Event event = new Event();
        event.setId(FIXTURE_EVENT_ORIGINAL_UUID);
        event.setAction(action);
        return event;
    }

    private static Endpoint buildCamelEndpoint(String accountId) {
        BasicAuthentication basicAuth = new BasicAuthentication(FIXTURE_CAMEL_BASIC_AUTH_USERNAME, FIXTURE_CAMEL_BASIC_AUTH_PASSWORD);

        CamelProperties properties = new CamelProperties();
        properties.setUrl(FIXTURE_CAMEL_URL);
        properties.setDisableSslVerification(FIXTURE_CAMEL_SSL_VERIFICATION);
        properties.setSecretToken(FIXTURE_CAMEL_SECRET_TOKEN);
        properties.setBasicAuthentication(basicAuth);
        properties.setExtras(Map.of(CamelTypeProcessor.PROCESSORNAME, FIXTURE_CAMEL_EXTRAS_PROCESSOR_NAME));

        Endpoint endpoint = new Endpoint();
        endpoint.setAccountId(accountId);
        endpoint.setOrgId(DEFAULT_ORG_ID);
        endpoint.setType(CAMEL);
        endpoint.setSubType(SUB_TYPE);
        endpoint.setProperties(properties);
        return endpoint;
    }

    private void checkBasicAuthentication(JsonObject notifMetadata, BasicAuthentication expectedBasicAuth) {
        String credentials = expectedBasicAuth.getUsername() + ":" + expectedBasicAuth.getPassword();
        String expectedBase64Credentials = Base64Utils.encode(credentials);
        assertEquals(expectedBase64Credentials, notifMetadata.getString("basicAuth"));
    }

    private static void checkKafkaMetadata(Message<String> message, UUID expectedMessageId, String expectedSubType) {
        Headers kafkaHeaders = message.getMetadata(KafkaMessageMetadata.class).get().getHeaders();

        // The 'rh-message-id' header should contain the notification history ID.
        byte[] messageId = kafkaHeaders.headers(MESSAGE_ID_HEADER).iterator().next().value();
        assertEquals(expectedMessageId, UUID.fromString(new String(messageId, UTF_8)));

        // The 'CAMEL_SUBTYPE' header should contain the endpoint subtype.
        assertEquals(expectedSubType, new String(kafkaHeaders.headers(CAMEL_SUBTYPE_HEADER).iterator().next().value(), UTF_8));
    }

    private void checkCloudEventMetadata(Message<String> message, UUID expectedId, String expectedAccountId, String expectedOrgId, String expectedSubType) {
        CloudEventMetadata metadata = message.getMetadata(CloudEventMetadata.class).get();
        assertEquals(expectedId.toString(), metadata.getId());
        assertEquals(expectedAccountId, metadata.getExtension(CLOUD_EVENT_ACCOUNT_EXTENSION_KEY).get());
        assertEquals(expectedOrgId, metadata.getExtension(CLOUD_EVENT_ORG_ID_EXTENSION_KEY).get());
        assertEquals(CLOUD_EVENT_TYPE_PREFIX + expectedSubType, metadata.getType());
    }

    private static void checkTracingMetadata(Message<String> message) {
        TracingMetadata metadata = message.getMetadata(TracingMetadata.class).get();
        assertNotNull(metadata.getPreviousContext());
    }
}
