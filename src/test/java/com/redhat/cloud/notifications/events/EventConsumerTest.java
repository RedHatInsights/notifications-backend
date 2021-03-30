package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.CounterAssertionHelper;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import static com.redhat.cloud.notifications.TestHelpers.serializeAction;
import static com.redhat.cloud.notifications.events.EventConsumer.PROCESSING_ERROR_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.REJECTED_COUNTER_NAME;
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

    @Inject
    CounterAssertionHelper counterAssertionHelper;

    @BeforeEach
    void init() {
        counterAssertionHelper.saveCounterValuesBeforeTest(REJECTED_COUNTER_NAME, PROCESSING_ERROR_COUNTER_NAME);
    }

    @AfterEach
    void clear() {
        counterAssertionHelper.clear();
    }

    @Test
    void testValidMessagePayload() throws IOException {
        Action action = buildValidAction();
        String serializedAction = serializeAction(action);
        inMemoryConnector.source("ingress").send(serializedAction);
        counterAssertionHelper.assertIncrement(REJECTED_COUNTER_NAME, 0);
        counterAssertionHelper.assertIncrement(PROCESSING_ERROR_COUNTER_NAME, 0);
        verify(destinations, times(1)).process(eq(action));
    }

    @Test
    void testInvalidMessagePayload() {
        inMemoryConnector.source("ingress").send("I am not a valid serialized action!");
        counterAssertionHelper.assertIncrement(REJECTED_COUNTER_NAME, 1);
        counterAssertionHelper.assertIncrement(PROCESSING_ERROR_COUNTER_NAME, 0);
        verify(destinations, never()).process(any(Action.class));
    }

    @Test
    void testProcessingError() throws IOException {
        Action action = buildValidAction();
        String serializedAction = serializeAction(action);
        when(destinations.process(eq(action))).thenReturn(
                Uni.createFrom().failure(() -> new RuntimeException("I am a forced exception!"))
        );
        inMemoryConnector.source("ingress").send(serializedAction);
        counterAssertionHelper.assertIncrement(REJECTED_COUNTER_NAME, 0);
        counterAssertionHelper.assertIncrement(PROCESSING_ERROR_COUNTER_NAME, 1);
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
}
