package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.auth.kessel.KesselAuthorization;
import com.redhat.cloud.notifications.auth.kessel.ResourceType;
import com.redhat.cloud.notifications.auth.kessel.WorkspacePermission;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.repositories.X509CertificateRepository;
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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.jboss.resteasy.reactive.RestPath;

import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN;
import static com.redhat.cloud.notifications.auth.kessel.Constants.WORKSPACE_ID_PLACEHOLDER;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

@Path(API_INTERNAL + "/x509Certificates")
public class X509CertificateResource {
    @Inject
    BackendConfig backendConfig;

    @Inject
    KesselAuthorization kesselAuthorization;

    @Inject
    X509CertificateRepository x509CertificateRepository;

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public X509Certificate createCertificate(@Context final SecurityContext securityContext, @NotNull @Valid X509Certificate certificate) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalCreateCertificate(certificate);
        } else {
            return this.legacyRBACCreateCertificate(certificate);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_ADMIN)
    public X509Certificate legacyRBACCreateCertificate(final X509Certificate certificate) {
        return this.internalCreateCertificate(certificate);
    }

    public X509Certificate internalCreateCertificate(final X509Certificate certificate) {
        return this.x509CertificateRepository.createCertificate(certificate);
    }

    @PUT
    @Path("/{certificateId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    public Response updateCertificate(@Context final SecurityContext securityContext, @RestPath UUID certificateId, @NotNull X509Certificate certificate) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalUpdateCertificate(certificateId, certificate);
        } else {
            return this.legacyRBACUpdateCertificate(certificateId, certificate);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_ADMIN)
    public Response legacyRBACUpdateCertificate(final UUID certificateId, final X509Certificate certificate) {
        return this.internalUpdateCertificate(certificateId, certificate);
    }

    public Response internalUpdateCertificate(final UUID certificateId, final X509Certificate certificate) {
        boolean updated = this.x509CertificateRepository.updateCertificate(certificateId, certificate);
        if (updated) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @DELETE
    @Path("/{certificateId}")
    public boolean deleteCertificate(@Context final SecurityContext securityContext, @RestPath UUID certificateId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalDeleteCertificate(certificateId);
        } else {
            return this.legacyRBACDeleteCertificate(certificateId);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_ADMIN)
    public boolean legacyRBACDeleteCertificate(final UUID certificateId) {
        return this.internalDeleteCertificate(certificateId);
    }

    public boolean internalDeleteCertificate(final UUID certificateId) {
        return this.x509CertificateRepository.deleteCertificate(certificateId);
    }
}
