package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.events.EndpointErrorFromConnectorHelper.CLIENT_TAG_VALUE;
import static com.redhat.cloud.notifications.events.EndpointErrorFromConnectorHelper.DISABLED_WEBHOOKS_COUNTER;
import static com.redhat.cloud.notifications.events.EndpointErrorFromConnectorHelper.DISABLE_ENDPOINT_CLIENT_ERRORS;
import static com.redhat.cloud.notifications.events.EndpointErrorFromConnectorHelper.ERROR_TYPE_TAG_KEY;
import static com.redhat.cloud.notifications.events.EndpointErrorFromConnectorHelper.HTTP_STATUS_CODE;
import static com.redhat.cloud.notifications.events.EndpointErrorFromConnectorHelper.INCREMENT_ENDPOINT_SERVER_ERRORS;
import static com.redhat.cloud.notifications.events.EndpointErrorFromConnectorHelper.SERVER_TAG_VALUE;
import static org.mockito.Mockito.*;

@QuarkusTest
class EndpointErrorFromConnectorHelperTest {

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @InjectMock
    EndpointRepository endpointRepository;

    @InjectMock
    IntegrationDisabledNotifier integrationDisabledNotifier;

    @Inject
    EndpointErrorFromConnectorHelper endpointErrorFromConnectorHelper;

    @BeforeEach
    void beforeEach() {
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(DISABLED_WEBHOOKS_COUNTER, ERROR_TYPE_TAG_KEY, SERVER_TAG_VALUE);
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(DISABLED_WEBHOOKS_COUNTER, ERROR_TYPE_TAG_KEY, CLIENT_TAG_VALUE);
    }

    @AfterEach
    void afterEach() {
        micrometerAssertionHelper.clearSavedValues();
    }

    @Test
    void testResetEndpointServerErrorCount() {
        final Endpoint endpoint = mockEndpointFromNotificationHistorySearch();

        JsonObject payload = buildTestPayload(true, 200, null, null);
        endpointErrorFromConnectorHelper.manageEndpointDisablingIfNeeded(endpoint, payload);
        verify(endpointRepository, times(1)).resetEndpointServerErrors(endpoint.getId());
        verifyNoInteractions(integrationDisabledNotifier);
        assertMetrics(0, 0);
    }

    @Test
    void testIncreaseServerErrorCount() {
        final Endpoint endpoint = mockEndpointFromNotificationHistorySearch();

        JsonObject payload = buildTestPayload(false, 503, true, null);
        endpointErrorFromConnectorHelper.manageEndpointDisablingIfNeeded(endpoint, payload);
        verify(endpointRepository, times(1)).incrementEndpointServerErrors(eq(endpoint.getId()), anyInt());
        verifyNoInteractions(integrationDisabledNotifier);
        assertMetrics(0, 0);
    }

    @Test
    void testIncreaseAndDisableServerErrorCount() {
        final Endpoint endpoint = mockEndpointFromNotificationHistorySearch();
        Mockito.when(endpointRepository.incrementEndpointServerErrors(eq(endpoint.getId()), anyInt())).thenReturn(true);

        JsonObject payload = buildTestPayload(false, 503, true, null);
        endpointErrorFromConnectorHelper.manageEndpointDisablingIfNeeded(endpoint, payload);
        verify(endpointRepository, times(1)).incrementEndpointServerErrors(eq(endpoint.getId()), anyInt());
        verify(integrationDisabledNotifier, times(1)).tooManyServerErrors(any(), anyInt());
        assertMetrics(1, 0);
    }

    @Test
    void testDisableEndpointBecauseOfClientError() {
        final Endpoint endpoint = mockEndpointFromNotificationHistorySearch();
        Mockito.when(endpointRepository.disableEndpoint(endpoint.getId())).thenReturn(true);

        JsonObject payload = buildTestPayload(false, 408, null, true);
        endpointErrorFromConnectorHelper.manageEndpointDisablingIfNeeded(endpoint, payload);
        verify(endpointRepository, times(1)).disableEndpoint(endpoint.getId());
        verify(integrationDisabledNotifier, times(1)).clientError(any(), eq(408));
        assertMetrics(0, 1);
    }

    @NotNull
    private Endpoint mockEndpointFromNotificationHistorySearch() {
        // Create an Endpoint which will be simulated to be fetched from the database.
        final String orgId = "test-org-id";
        final UUID endpointUuid = UUID.randomUUID();

        final Endpoint endpointFixture = new Endpoint();
        endpointFixture.setId(endpointUuid);
        endpointFixture.setOrgId(orgId);
        endpointFixture.setType(EndpointType.WEBHOOK);

        return endpointFixture;
    }


    private JsonObject buildTestPayload(boolean successful, Integer httpReturnCode, Boolean addServerErrorFlag, Boolean addClientErrorFlag) {
        String expectedHistoryId = UUID.randomUUID().toString();
        String expectedDetailsType = "com.redhat.console.notification.toCamel.tower";
        String expectedDetailsTarget = "1.2.3.4";

        HashMap<String, Object> dataMap = new HashMap<>(Map.of(
                "duration", 1234,
                "finishTime", 1639476503209L,
                "details", Map.of(
                        "type", expectedDetailsType,
                        "target", expectedDetailsTarget,
                        HTTP_STATUS_CODE, httpReturnCode
                ),
                "successful", successful,
            "outcome", "this is a test"
        ));

        if (null !=  addServerErrorFlag) {
            dataMap.put(INCREMENT_ENDPOINT_SERVER_ERRORS, addServerErrorFlag);
        }
        if (null !=  addClientErrorFlag) {
            dataMap.put(DISABLE_ENDPOINT_CLIENT_ERRORS, addClientErrorFlag);
        }

        String payload = Json.encode(Map.of(
                "specversion", "1.0",
                "source", "demo-log",
                "type", "com.redhat.cloud.notifications.history",
                "time", "2021-12-14T10:08:23.217Z",
                "id", expectedHistoryId,
                "content-type", "application/json",
                "data", Json.encode(dataMap)
        ));
        return new JsonObject(payload);
    }

    private void assertMetrics(int expectedServerErrorIncrement, int expectedClientErrorIncrement) {
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(DISABLED_WEBHOOKS_COUNTER, ERROR_TYPE_TAG_KEY, SERVER_TAG_VALUE, expectedServerErrorIncrement);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(DISABLED_WEBHOOKS_COUNTER, ERROR_TYPE_TAG_KEY, CLIENT_TAG_VALUE, expectedClientErrorIncrement);
    }
}
