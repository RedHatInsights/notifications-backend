package com.redhat.cloud.notifications.auth.kessel;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.project_kessel.api.inventory.v1beta2.Allowed;
import org.project_kessel.api.inventory.v1beta2.CheckForUpdateRequest;
import org.project_kessel.api.inventory.v1beta2.CheckForUpdateResponse;
import org.project_kessel.api.inventory.v1beta2.CheckRequest;
import org.project_kessel.api.inventory.v1beta2.CheckResponse;
import org.project_kessel.api.inventory.v1beta2.KesselInventoryServiceGrpc;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.redhat.cloud.notifications.auth.kessel.KesselCheckClient.KESSEL_CHANNEL_INIT_COUNTER_NAME;
import static com.redhat.cloud.notifications.auth.kessel.KesselCheckClient.KESSEL_CHANNEL_INIT_TAG_REASON;
import static com.redhat.cloud.notifications.auth.kessel.KesselCheckClient.KESSEL_GRPC_ERROR_COUNTER_NAME;
import static com.redhat.cloud.notifications.auth.kessel.KesselCheckClient.KESSEL_GRPC_ERROR_TAG_ERROR_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests retry behavior, channel reinitialization, and metrics in KesselCheckClient.
 *
 * Uses a test profile with insecure mode to avoid OAuth2 during startup,
 * then injects mock gRPC stub and channel via reflection.
 */
@QuarkusTest
@TestProfile(KesselCheckClientTest.InsecureKesselProfile.class)
public class KesselCheckClientTest {

    /**
     * Test profile that enables insecure mode to avoid OAuth2 initialization.
     */
    public static class InsecureKesselProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "notifications.kessel.insecure-client.enabled", "true",
                "notifications.kessel.url", "localhost:9999",
                "notifications.kessel.timeout-ms", "5000"
            );
        }
    }

    @Inject
    KesselCheckClient kesselCheckClient;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    private KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub mockStub;
    private ManagedChannel mockChannel;

    @BeforeEach
    void setUp() throws Exception {
        // Create mock gRPC stub
        mockStub = mock(KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub.class);
        when(mockStub.withDeadlineAfter(Mockito.anyLong(), any())).thenReturn(mockStub);

        // Create mock channel that appears healthy
        mockChannel = mock(ManagedChannel.class);
        when(mockChannel.getState(false)).thenReturn(ConnectivityState.READY);

        // Unwrap the CDI proxy to get the actual bean instance
        KesselCheckClient actualBean = ClientProxy.unwrap(kesselCheckClient);

        // Inject mocks via reflection into the actual bean
        injectField(actualBean, "grpcClient", mockStub);
        injectField(actualBean, "grpcChannel", mockChannel);

        // Save current counter values before each test
        saveCounterValues();
    }

    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = KesselCheckClient.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void saveCounterValues() {
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(KESSEL_GRPC_ERROR_COUNTER_NAME, KESSEL_GRPC_ERROR_TAG_ERROR_TYPE, "UNAVAILABLE");
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(KESSEL_GRPC_ERROR_COUNTER_NAME, KESSEL_GRPC_ERROR_TAG_ERROR_TYPE, "DEADLINE_EXCEEDED");
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(KESSEL_GRPC_ERROR_COUNTER_NAME, KESSEL_GRPC_ERROR_TAG_ERROR_TYPE, "RESOURCE_EXHAUSTED");
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(KESSEL_GRPC_ERROR_COUNTER_NAME, KESSEL_GRPC_ERROR_TAG_ERROR_TYPE, "ABORTED");
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(KESSEL_GRPC_ERROR_COUNTER_NAME, KESSEL_GRPC_ERROR_TAG_ERROR_TYPE, "PERMISSION_DENIED");
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(KESSEL_GRPC_ERROR_COUNTER_NAME, KESSEL_GRPC_ERROR_TAG_ERROR_TYPE, "NOT_FOUND");
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(KESSEL_GRPC_ERROR_COUNTER_NAME, KESSEL_GRPC_ERROR_TAG_ERROR_TYPE, "INVALID_ARGUMENT");
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(KESSEL_GRPC_ERROR_COUNTER_NAME, KESSEL_GRPC_ERROR_TAG_ERROR_TYPE, "UNAUTHENTICATED");
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(KESSEL_CHANNEL_INIT_COUNTER_NAME, KESSEL_CHANNEL_INIT_TAG_REASON, "unauthenticated");
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(KESSEL_CHANNEL_INIT_COUNTER_NAME, KESSEL_CHANNEL_INIT_TAG_REASON, "unhealthy_channel");
    }

    private void assertErrorCount(String errorType, double expectedIncrement) {
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(KESSEL_GRPC_ERROR_COUNTER_NAME, KESSEL_GRPC_ERROR_TAG_ERROR_TYPE, errorType, expectedIncrement);
    }

    static Status[] transientErrors() {
        return new Status[]{Status.UNAVAILABLE, Status.DEADLINE_EXCEEDED, Status.RESOURCE_EXHAUSTED, Status.ABORTED};
    }

    static Status[] nonTransientErrors() {
        return new Status[]{Status.PERMISSION_DENIED, Status.NOT_FOUND, Status.INVALID_ARGUMENT};
    }

    @Test
    void testCheckSuccessfulCallNoRetry() {
        CheckResponse successResponse = CheckResponse.newBuilder()
            .setAllowed(Allowed.ALLOWED_TRUE)
            .build();
        when(mockStub.check(any())).thenReturn(successResponse);

        CheckResponse result = kesselCheckClient.check(CheckRequest.getDefaultInstance());

        assertEquals(Allowed.ALLOWED_TRUE, result.getAllowed());
        verify(mockStub, times(1)).check(any());

        // No errors recorded
        assertErrorCount("UNAVAILABLE", 0);
        assertErrorCount("DEADLINE_EXCEEDED", 0);
        assertErrorCount("RESOURCE_EXHAUSTED", 0);
        assertErrorCount("ABORTED", 0);
        assertErrorCount("UNAUTHENTICATED", 0);
        assertErrorCount("PERMISSION_DENIED", 0);
        assertErrorCount("NOT_FOUND", 0);
        assertErrorCount("INVALID_ARGUMENT", 0);
    }

    @ParameterizedTest
    @MethodSource("transientErrors")
    void testCheckRetryOnTransientErrors(Status status) {
        CheckResponse successResponse = CheckResponse.newBuilder()
            .setAllowed(Allowed.ALLOWED_TRUE)
            .build();

        // Fail twice, then succeed
        when(mockStub.check(any()))
            .thenThrow(new StatusRuntimeException(status))
            .thenThrow(new StatusRuntimeException(status))
            .thenReturn(successResponse);

        CheckResponse result = kesselCheckClient.check(CheckRequest.getDefaultInstance());

        assertEquals(Allowed.ALLOWED_TRUE, result.getAllowed());
        verify(mockStub, times(3)).check(any());
        assertErrorCount(status.getCode().name(), 2);
    }

    @ParameterizedTest
    @MethodSource("nonTransientErrors")
    void testCheckNoRetryOnNonTransientErrors(Status status) {
        when(mockStub.check(any()))
            .thenThrow(new StatusRuntimeException(status));

        assertThrows(StatusRuntimeException.class, () ->
            kesselCheckClient.check(CheckRequest.getDefaultInstance())
        );

        verify(mockStub, times(1)).check(any());
        assertErrorCount(status.getCode().name(), 1);
    }

    @Test
    void testCheckExhaustedRetriesThrowsException() {
        // Always fail with UNAVAILABLE - should exhaust retries
        when(mockStub.check(any()))
            .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThrows(KesselTransientException.class, () ->
            kesselCheckClient.check(CheckRequest.getDefaultInstance())
        );

        // 1 initial call + 3 retries = 4 total calls
        verify(mockStub, times(4)).check(any());

        // 4 UNAVAILABLE errors recorded
        assertErrorCount("UNAVAILABLE", 4);
    }

    @Test
    void testCheckUsesConfiguredTimeout() {
        CheckResponse successResponse = CheckResponse.newBuilder()
            .setAllowed(Allowed.ALLOWED_TRUE)
            .build();
        when(mockStub.check(any())).thenReturn(successResponse);

        kesselCheckClient.check(CheckRequest.getDefaultInstance());

        // Verify timeout from config (5000ms in test profile) is used
        verify(mockStub).withDeadlineAfter(eq(5000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void testCheckForUpdateSuccessfulCallNoRetry() {
        CheckForUpdateResponse successResponse = CheckForUpdateResponse.newBuilder()
            .setAllowed(Allowed.ALLOWED_TRUE)
            .build();
        when(mockStub.checkForUpdate(any())).thenReturn(successResponse);

        CheckForUpdateResponse result = kesselCheckClient.checkForUpdate(CheckForUpdateRequest.getDefaultInstance());

        assertEquals(Allowed.ALLOWED_TRUE, result.getAllowed());
        verify(mockStub, times(1)).checkForUpdate(any());

        // No errors recorded
        assertErrorCount("UNAVAILABLE", 0);
        assertErrorCount("DEADLINE_EXCEEDED", 0);
        assertErrorCount("RESOURCE_EXHAUSTED", 0);
        assertErrorCount("ABORTED", 0);
        assertErrorCount("UNAUTHENTICATED", 0);
        assertErrorCount("PERMISSION_DENIED", 0);
        assertErrorCount("NOT_FOUND", 0);
        assertErrorCount("INVALID_ARGUMENT", 0);
    }

    @ParameterizedTest
    @MethodSource("transientErrors")
    void testCheckForUpdateRetryOnTransientErrors(Status status) {
        CheckForUpdateResponse successResponse = CheckForUpdateResponse.newBuilder()
            .setAllowed(Allowed.ALLOWED_TRUE)
            .build();

        // Fail twice, then succeed
        when(mockStub.checkForUpdate(any()))
            .thenThrow(new StatusRuntimeException(status))
            .thenThrow(new StatusRuntimeException(status))
            .thenReturn(successResponse);

        CheckForUpdateResponse result = kesselCheckClient.checkForUpdate(CheckForUpdateRequest.getDefaultInstance());

        assertEquals(Allowed.ALLOWED_TRUE, result.getAllowed());
        verify(mockStub, times(3)).checkForUpdate(any());
        assertErrorCount(status.getCode().name(), 2);
    }

    @ParameterizedTest
    @MethodSource("nonTransientErrors")
    void testCheckForUpdateNoRetryOnNonTransientErrors(Status status) {
        when(mockStub.checkForUpdate(any()))
            .thenThrow(new StatusRuntimeException(status));

        assertThrows(StatusRuntimeException.class, () ->
            kesselCheckClient.checkForUpdate(CheckForUpdateRequest.getDefaultInstance())
        );

        verify(mockStub, times(1)).checkForUpdate(any());
        assertErrorCount(status.getCode().name(), 1);
    }

    @Test
    void testCheckForUpdateExhaustedRetriesThrowsException() {
        // Always fail with UNAVAILABLE - should exhaust retries
        when(mockStub.checkForUpdate(any()))
            .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThrows(KesselTransientException.class, () ->
            kesselCheckClient.checkForUpdate(CheckForUpdateRequest.getDefaultInstance())
        );

        // 1 initial call + 3 retries = 4 total calls
        verify(mockStub, times(4)).checkForUpdate(any());

        // 4 UNAVAILABLE errors recorded
        assertErrorCount("UNAVAILABLE", 4);
    }

    @Test
    void testCheckForUpdateUsesConfiguredTimeout() {
        CheckForUpdateResponse successResponse = CheckForUpdateResponse.newBuilder()
            .setAllowed(Allowed.ALLOWED_TRUE)
            .build();
        when(mockStub.checkForUpdate(any())).thenReturn(successResponse);

        kesselCheckClient.checkForUpdate(CheckForUpdateRequest.getDefaultInstance());

        // Verify timeout from config (5000ms in test profile) is used
        verify(mockStub).withDeadlineAfter(eq(5000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void testUnauthenticatedTriggersChannelReinit() {
        // UNAUTHENTICATED triggers channel reinitialization
        when(mockStub.check(any()))
            .thenThrow(new StatusRuntimeException(Status.UNAUTHENTICATED));

        // After UNAUTHENTICATED, initializeChannel replaces mock with real client.
        // Retries will fail (no server), eventually throwing KesselTransientException.
        assertThrows(KesselTransientException.class, () ->
            kesselCheckClient.check(CheckRequest.getDefaultInstance())
        );

        // Verify UNAUTHENTICATED was recorded
        assertErrorCount("UNAUTHENTICATED", 1);

        // Verify channel was reinitialized due to unauthenticated error
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(
            KESSEL_CHANNEL_INIT_COUNTER_NAME, KESSEL_CHANNEL_INIT_TAG_REASON, "unauthenticated", 1);
    }

    @Test
    void testUnhealthyChannelTriggersReinit() {
        // Simulate channel in SHUTDOWN state (terminal, cannot recover)
        when(mockChannel.getState(false)).thenReturn(ConnectivityState.SHUTDOWN);

        // getClient() will detect unhealthy channel and reinitialize.
        // After reinit, real client is used which fails (no server).
        assertThrows(KesselTransientException.class, () ->
            kesselCheckClient.check(CheckRequest.getDefaultInstance())
        );

        // Verify channel was reinitialized due to unhealthy channel
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(
            KESSEL_CHANNEL_INIT_COUNTER_NAME, KESSEL_CHANNEL_INIT_TAG_REASON, "unhealthy_channel", 1);
    }

    @Test
    void testChannelReinitShutsDownOldChannel() {
        // Simulate channel in SHUTDOWN state to trigger reinit
        when(mockChannel.getState(false)).thenReturn(ConnectivityState.SHUTDOWN);

        // This will trigger getClient() -> initializeChannel("unhealthy_channel")
        // which should shutdown the old channel
        assertThrows(KesselTransientException.class, () ->
            kesselCheckClient.check(CheckRequest.getDefaultInstance())
        );

        // Verify old channel was shutdown
        verify(mockChannel).shutdown();
    }
}
