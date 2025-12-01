package com.redhat.cloud.notifications.routers.internal.kessel;

import com.redhat.cloud.notifications.auth.kessel.OAuth2ClientCredentialsCache;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN;

@Path(API_INTERNAL + "/kessel/oauth2-client-credentials")
@RolesAllowed(RBAC_INTERNAL_ADMIN)
public class KesselResource {

    @Inject
    OAuth2ClientCredentialsCache oauth2ClientCredentialsCache;

    @DELETE
    @Path("/clear-cache")
    public void clearCache() {
        oauth2ClientCredentialsCache.clearCache();
        Log.info("Kessel OAuth2 client credentials cache cleared");
    }
}
