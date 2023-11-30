package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.db.repositories.x509CertificateRepository;
import com.redhat.cloud.notifications.models.X509Certificate;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
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

@Path(API_INTERNAL + "/x509Certificates")
@RolesAllowed(RBAC_INTERNAL_ADMIN)
public class X509CertificateResource {

    @Inject
    x509CertificateRepository x509CertificateRepository;

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public X509Certificate createCertificate(@NotNull @Valid X509Certificate certificate) {
        return x509CertificateRepository.createCertificate(certificate);
    }

    @PUT
    @Path("/{certificateId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    public Response updateCertificate(@RestPath UUID certificateId, X509Certificate certificate) {
        boolean updated = x509CertificateRepository.updateCertificate(certificateId, certificate);
        if (updated) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @DELETE
    @Path("/{certificateId}")
    public boolean deleteTemplate(@RestPath UUID certificateId) {
        return x509CertificateRepository.deleteCertificate(certificateId);
    }
}
