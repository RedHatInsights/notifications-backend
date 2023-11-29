package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.db.repositories.GatewayCertificateRepository;
import com.redhat.cloud.notifications.models.GatewayCertificate;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestPath;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

@Path(API_INTERNAL + "/gatewayCertificate")
@RolesAllowed(RBAC_INTERNAL_ADMIN)
public class GatewayCertificateResource {

    @Inject
    GatewayCertificateRepository gatewayCertificateRepository;

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_ADMIN)
    public GatewayCertificate createCertificate(@NotNull @Valid GatewayCertificate certificate) {
        return gatewayCertificateRepository.createGatewayCertificate(certificate);
    }

    @PUT
    @Path("/{gatewayCertificateId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_ADMIN)
    public Response updateCertificate(@RestPath UUID gatewayCertificateId, GatewayCertificate gatewayCertificate) {
        boolean updated = gatewayCertificateRepository.updateGatewayCertificate(gatewayCertificateId, gatewayCertificate);
        if (updated) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @DELETE
    @Path("/{gatewayCertificateId}")
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_ADMIN)
    public boolean deleteTemplate(@RestPath UUID gatewayCertificateId) {
        return gatewayCertificateRepository.deleteGatewayCertificate(gatewayCertificateId);
    }
}
