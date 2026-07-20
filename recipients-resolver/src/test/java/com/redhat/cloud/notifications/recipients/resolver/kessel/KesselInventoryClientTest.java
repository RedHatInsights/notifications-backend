package com.redhat.cloud.notifications.recipients.resolver.kessel;

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
import org.project_kessel.api.inventory.v1beta2.KesselInventoryServiceGrpc;
import org.project_kessel.api.inventory.v1beta2.StreamedListSubjectsRequest;
import org.project_kessel.api.inventory.v1beta2.StreamedListSubjectsResponse;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests channel lifecycle and gRPC exception mapping in KesselInventoryClient.
 *
 * Uses a test profile with insecure mode to avoid OAuth2 during startup,
 * then injects mock gRPC stub and channel via reflection.
 */
@QuarkusTest
@TestProfile(KesselInventoryClientTest.InsecureKesselProfile.class)
public class KesselInventoryClientTest {

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
    KesselInventoryClient kesselInventoryClient;

    private KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub mockStub;
    private ManagedChannel mockChannel;

    static Status[] transientErrors() {
        return new Status[]{Status.UNAVAILABLE, Status.DEADLINE_EXCEEDED, Status.RESOURCE_EXHAUSTED, Status.ABORTED};
    }

    static Status[] nonTransientErrors() {
        return new Status[]{Status.PERMISSION_DENIED, Status.NOT_FOUND, Status.INVALID_ARGUMENT};
    }

    @BeforeEach
    void setUp() throws Exception {
        mockStub = mock(KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub.class);
        when(mockStub.withDeadlineAfter(Mockito.anyLong(), any())).thenReturn(mockStub);

        mockChannel = mock(ManagedChannel.class);
        when(mockChannel.getState(false)).thenReturn(ConnectivityState.READY);

        KesselInventoryClient actualBean = ClientProxy.unwrap(kesselInventoryClient);
        injectField(actualBean, "grpcClient", mockStub);
        injectField(actualBean, "grpcChannel", mockChannel);
    }

    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = KesselInventoryClient.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void testStreamedListSubjectsPassesThroughToStubWithConfiguredTimeout() {
        Iterator<StreamedListSubjectsResponse> responseIterator = Collections.emptyIterator();
        when(mockStub.streamedListSubjects(any())).thenReturn(responseIterator);

        StreamedListSubjectsRequest request = StreamedListSubjectsRequest.getDefaultInstance();
        Iterator<StreamedListSubjectsResponse> result = kesselInventoryClient.streamedListSubjects(request);

        assertSame(responseIterator, result);
        verify(mockStub).withDeadlineAfter(eq(5000L), eq(TimeUnit.MILLISECONDS));
        verify(mockStub).streamedListSubjects(request);
    }

    @Test
    void testUnhealthyChannelTriggersReinitBeforeStreaming() {
        when(mockChannel.getState(false)).thenReturn(ConnectivityState.SHUTDOWN);
        when(mockStub.streamedListSubjects(any())).thenReturn(Collections.emptyIterator());

        kesselInventoryClient.streamedListSubjects(StreamedListSubjectsRequest.getDefaultInstance());

        // The unhealthy (SHUTDOWN) channel must have been replaced, not reused.
        verify(mockChannel).shutdown();
    }

    @ParameterizedTest
    @MethodSource("transientErrors")
    void testHandleGrpcExceptionMapsTransientErrorsToKesselTransientException(Status status) {
        StatusRuntimeException exception = new StatusRuntimeException(status);

        RuntimeException mapped = kesselInventoryClient.handleGrpcException(exception);

        assertInstanceOf(KesselTransientException.class, mapped);
        assertSame(exception, mapped.getCause());
    }

    @ParameterizedTest
    @MethodSource("nonTransientErrors")
    void testHandleGrpcExceptionReturnsNonTransientErrorsAsIs(Status status) {
        StatusRuntimeException exception = new StatusRuntimeException(status);

        RuntimeException mapped = kesselInventoryClient.handleGrpcException(exception);

        assertSame(exception, mapped);
    }

    @Test
    void testHandleGrpcExceptionUnauthenticatedTriggersChannelReinit() {
        StatusRuntimeException exception = new StatusRuntimeException(Status.UNAUTHENTICATED);

        RuntimeException mapped = kesselInventoryClient.handleGrpcException(exception);

        assertInstanceOf(KesselTransientException.class, mapped);
        verify(mockChannel).shutdown();
    }

    @Test
    void testHandleGrpcExceptionUnauthenticatedReplacesGrpcClient() throws Exception {
        kesselInventoryClient.handleGrpcException(new StatusRuntimeException(Status.UNAUTHENTICATED));

        KesselInventoryClient actualBean = ClientProxy.unwrap(kesselInventoryClient);
        Field clientField = KesselInventoryClient.class.getDeclaredField("grpcClient");
        clientField.setAccessible(true);
        Object newClient = clientField.get(actualBean);

        assertNotSame(mockStub, newClient);
    }
}
