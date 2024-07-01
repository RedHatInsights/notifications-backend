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
public class PayloadDetailsResource {
    @Inject
    PayloadDetailsMapper payloadDetailsMapper;

    @Inject
    PayloadDetailsRepository payloadDetailsRepository;

    /**
     * Retrieves the payload's contents.
     * @param payloadDetailsId the payload's ID to fetch the contents for.
     * @return a DTO containing the payload details.
     */
    @GET
    @Path("/{payloadDetailsId}")
    @Produces(MediaType.APPLICATION_JSON)
    public ReadPayloadDetailsDto getPayloadForEvent(@NotNull @RestPath UUID payloadDetailsId) {
        final Optional<PayloadDetails> payloadDetailsOptional = this.payloadDetailsRepository.findById(payloadDetailsId);

        final PayloadDetails payloadDetails = payloadDetailsOptional.orElseThrow(NotFoundException::new);

        return this.payloadDetailsMapper.toDto(payloadDetails);
    }
}
