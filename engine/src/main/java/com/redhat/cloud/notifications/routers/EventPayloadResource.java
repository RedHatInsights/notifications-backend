package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.repositories.PayloadDetailsRepository;
import com.redhat.cloud.notifications.processors.payload.PayloadDetails;
import com.redhat.cloud.notifications.processors.payload.PayloadDetailsMapper;
import com.redhat.cloud.notifications.processors.payload.dto.v1.ReadPayloadDetailsDto;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestPath;

import java.util.Optional;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;

@Path(API_INTERNAL + "/payloads")
public class EventPayloadResource {
    @Inject
    PayloadDetailsMapper payloadDetailsMapper;

    @Inject
    PayloadDetailsRepository payloadDetailsRepository;

    /**
     * Retrieves the payload contents for the given event.
     * @param eventId the event ID to fetch the payload for.
     * @return a DTO containing the payload details.
     */
    @GET
    @Path("/{eventId}")
    @Produces(MediaType.APPLICATION_JSON)
    public ReadPayloadDetailsDto getPayloadForEvent(@NotNull @RestPath UUID eventId) {
        final Optional<PayloadDetails> payloadDetailsOptional = this.payloadDetailsRepository.findByEventId(eventId);

        final PayloadDetails payloadDetails = payloadDetailsOptional.orElseThrow(NotFoundException::new);

        return this.payloadDetailsMapper.toDto(payloadDetails);
    }
}
