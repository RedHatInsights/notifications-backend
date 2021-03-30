package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.ingress.Action;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import static com.redhat.cloud.notifications.TestHelpers.serializeAction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventConsumerTest {

    @Inject
    @Connector("smallrye-in-memory")
    InMemoryConnector inMemoryConnector;

    @InjectMock
    EndpointProcessor destinations;

    private Counter rejectedCounter = Metrics.counter("input.rejected");
    private Counter processingErrorCounter = Metrics.counter("input.processing.error");

    @Test
    void testValidMessagePayload() throws IOException {
        Action action = buildValidAction();
        String serializedAction = serializeAction(action);
        assertCountersIncrement(0, 0,
                () -> inMemoryConnector.source("ingress").send(serializedAction));
        verify(destinations, times(1)).process(eq(action));
    }

    @Test
    void testInvalidMessagePayload() {
        assertCountersIncrement(1, 0,
                () -> inMemoryConnector.source("ingress").send("I am not a valid serialized action!"));
        verify(destinations, never()).process(any(Action.class));
    }

    @Test
    void testProcessingError() throws IOException {
        Action action = buildValidAction();
        String serializedAction = serializeAction(action);
        when(destinations.process(eq(action))).thenReturn(
                Uni.createFrom().failure(() -> new RuntimeException("I am a forced exception!"))
        );
        assertCountersIncrement(0, 1,
                () -> inMemoryConnector.source("ingress").send(serializedAction));
        verify(destinations, times(1)).process(eq(action));
    }

    private static Action buildValidAction() {
        Action action = new Action();
        action.setBundle("my-bundle");
        action.setApplication("Policies");
        action.setEventType("Any");
        action.setTimestamp(LocalDateTime.now());
        action.setAccountId("testTenant");
        action.setPayload(Map.of("k", "v", "k2", "v2", "k3", "v"));
        return action;
    }

    private void assertCountersIncrement(int expectedRejectedIncrement, int expectedProcessingErrorIncrement, Runnable runnable) {
        double initialRejectedCount = rejectedCounter.count();
        double initialProcessingErrorCount = processingErrorCounter.count();
        runnable.run();
        assertEquals(expectedRejectedIncrement, rejectedCounter.count() - initialRejectedCount);
        assertEquals(expectedProcessingErrorIncrement, processingErrorCounter.count() - initialProcessingErrorCount);
    }
}
