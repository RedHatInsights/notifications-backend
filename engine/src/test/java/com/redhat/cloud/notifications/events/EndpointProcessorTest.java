package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.event.TestEventHelper;
import com.redhat.cloud.notifications.processors.rhose.RhoseTypeProcessor;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.inject.Inject;
import java.util.UUID;

@QuarkusTest
public class EndpointProcessorTest {

    @Inject
    EndpointProcessor endpointProcessor;

    /**
     * The processor is mocked to avoid {@link #testTestEndpointFetchedById()}
     * throwing exceptions due to passing fixture data to the RHOSE processor.
     */
    @InjectMock
    RhoseTypeProcessor rhoseTypeProcessor;

    @InjectMock
    EndpointRepository endpointRepository;

    /**
     * Tests that when an "integration customer test" event is processed, the
     * corresponding endpoint is fetched by the UUID that gets set in the
     * action's context.
     */
    @Test
    void testTestEndpointFetchedById() {
        // Create an Endpoint which will be simulated to be fetched from the database.
        final String orgId = "test-org-id";
        final UUID endpointUuid = UUID.randomUUID();

        final Endpoint endpointFixture = new Endpoint();
        endpointFixture.setId(endpointUuid);
        endpointFixture.setOrgId(orgId);
        endpointFixture.setSubType("slack");
        endpointFixture.setType(EndpointType.CAMEL);

        // Avoids the "NulLPointerException" in the "List.of" statement.
        Mockito.when(this.endpointRepository.findByUuidAndOrgId(endpointUuid, orgId)).thenReturn(endpointFixture);

        // Create the action with the endpoint reference and then a corresponding event for the processor.
        final Action testAction = TestEventHelper.createTestAction(endpointUuid, orgId);

        // Convert the action to JSON and back to simulate the event going
        // through Kafka. If not, some additional properties of the context are
        // not serialized as String, and won't match all the types and the way
        // they get serialized when sent via Kafka and received via Kafka as
        // well.
        final String jsonAction = Json.encode(testAction);
        final Action rawAction = Json.decodeValue(jsonAction, Action.class);

        final Event event = new Event();
        event.setAction(rawAction);
        event.setId(rawAction.getId());
        event.setOrgId(orgId);

        this.endpointProcessor.process(event);

        Mockito.verify(this.endpointRepository, Mockito.times(1)).findByUuidAndOrgId(endpointUuid, orgId);
        Mockito.verify(this.endpointRepository, Mockito.times(0)).getTargetEndpoints(Mockito.anyString(), Mockito.any(EventType.class));
    }

    /**
     * Tests that when a regular, non-test event is received, the corresponding
     * target endpoints get fetched by the event's org id and event type.
     */
    @Test
    void testEndpointFetchedAsRegular() {
        // Create an Endpoint which will be simulated to be fetched from the database.
        final String orgId = "test-org-id";

        // Create an action.
        final Action testAction = TestEventHelper.createTestAction(UUID.randomUUID(), orgId);
        testAction.setApplication("non test application");
        testAction.setBundle("non test bundle");
        testAction.setEventType("non test event type");

        final UUID eventTypeId = UUID.randomUUID();
        final EventType eventType = new EventType();
        eventType.setId(eventTypeId);

        final Event event = new Event();
        event.setAction(testAction);
        event.setEventType(eventType);
        event.setId(testAction.getId());
        event.setOrgId(orgId);

        this.endpointProcessor.process(event);

        Mockito.verify(this.endpointRepository, Mockito.times(0)).findByUuidAndOrgId(Mockito.any(), Mockito.anyString());
        Mockito.verify(this.endpointRepository, Mockito.times(1)).getTargetEndpoints(orgId, eventType);
    }
}
