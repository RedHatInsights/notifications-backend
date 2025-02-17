package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.events.ConnectorReceiver;
import com.redhat.cloud.notifications.events.GeneralCommunicationsHelper;
import com.redhat.cloud.notifications.events.KafkaMessageWithIdBuilder;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.routers.general.communication.SendGeneralCommunicationResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;

@Path(API_INTERNAL + "/general-communications")
public class GeneralCommunicationsResource {
    /**
     * Defines the message that will be sent back to the back end when no
     * general communication was sent.
     */
    public static final String NO_OP_RESPONSE_MESSAGE = "No general communication was sent because no results were returned from the database";

    @Channel(ConnectorReceiver.EGRESS_CHANNEL)
    @Inject
    Emitter<String> eventEmitter;

    @Inject
    EndpointRepository endpointRepository;

    /**
     * Creates a "general communication" action and sends it to the ingress
     * Kafka channel.
     */
    @Path("/send")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public SendGeneralCommunicationResponse sendGeneralCommunication() {
        // Find all the integration names and group them by organization ID.
        final CompositeEndpointType compositeEndpointType = new CompositeEndpointType(EndpointType.CAMEL, "teams");
        final Map<String, List<String>> endpointNamesPerOrg = this.endpointRepository.findIntegrationNamesByTypeGroupedByOrganizationId(compositeEndpointType);

        // Send a "no content" response when there is nothing to do. It is not
        // an error per se, so that is
        if (endpointNamesPerOrg.isEmpty()) {
            return new SendGeneralCommunicationResponse(NO_OP_RESPONSE_MESSAGE);
        }

        // Encode an event per organization, and send it to Kafka.
        for (final Map.Entry<String, List<String>> entry : endpointNamesPerOrg.entrySet()) {
            final String encodedAction = Parser.encode(GeneralCommunicationsHelper.createGeneralCommunicationAction(entry.getKey(), compositeEndpointType, entry.getValue()));

            final Message<String> message = KafkaMessageWithIdBuilder.build(encodedAction);
            this.eventEmitter.send(message);
        }

        return new SendGeneralCommunicationResponse(null);
    }
}
