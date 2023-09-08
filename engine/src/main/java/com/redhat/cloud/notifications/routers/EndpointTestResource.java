package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.events.ConnectorReceiver;
import com.redhat.cloud.notifications.events.KafkaMessageWithIdBuilder;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.models.event.TestEventHelper;
import com.redhat.cloud.notifications.routers.endpoints.InternalEndpointTestRequest;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(API_INTERNAL + "/endpoints")
public class EndpointTestResource {

    @Channel(ConnectorReceiver.EGRESS_CHANNEL)
    @Inject
    Emitter<String> eventEmitter;

    /**
     * Creates a "endpoint integration test" action and sends it to the ingress
     * Kafka channel, based on the received payload.
     * @param internalEndpointTestRequest the payload to create the test event from.
     */
    @APIResponse(responseCode = "204")
    @Consumes(APPLICATION_JSON)
    @Path("/test")
    @POST
    public void testEndpoint(@Valid final InternalEndpointTestRequest internalEndpointTestRequest) {
        final Action testAction;
        if (internalEndpointTestRequest.isMessageBlank()) {
            testAction = TestEventHelper.createTestAction(
                internalEndpointTestRequest.endpointUuid,
                internalEndpointTestRequest.orgId
            );
        } else {
            testAction = TestEventHelper.createTestAction(
                internalEndpointTestRequest.endpointUuid,
                internalEndpointTestRequest.message,
                internalEndpointTestRequest.orgId
            );
        }

        final String encodedAction = Parser.encode(testAction);
        final Message<String> message = KafkaMessageWithIdBuilder.build(encodedAction);

        this.eventEmitter.send(message);
    }
}
