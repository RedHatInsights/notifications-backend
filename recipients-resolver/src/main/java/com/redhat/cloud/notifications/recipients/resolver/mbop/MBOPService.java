package com.redhat.cloud.notifications.recipients.resolver.mbop;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

/**
 * REST client for both BOP and MBOP services, which in turn, talk to the IT
 * service.
 */
@RegisterRestClient(configKey = "mbop")
public interface MBOPService {
    /**
     * Gets the users of a given tenant by its organization ID.
     * @param apiToken the API token which MBOP will use to communicate with
     *                 authentication services.
     * @param clientId the Client ID which MBOP will use to communicate with
     *                 authentication services.
     * @param environment the environment in which the application is running.
     * @param orgId the organization to look the users from.
     * @param adminOnly do we want to fetch just organization administrators?
     * @param limit number of records to return per page. Defaults to 100.
     * @param offset the offset to apply to the requested records. Defaults
     *               to 0.
     * @param includePermissions always false
     * @param status always enabled
     * @return the list of requested users.
     */
    @Path("/v3/accounts/{orgId}/users")
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    MBOPUsers getUsersByOrgId(
        @HeaderParam(Constants.MBOP_APITOKEN_HEADER)    String apiToken,
        @HeaderParam(Constants.MBOP_CLIENT_ID_HEADER)   String clientId,
        @HeaderParam(Constants.MBOP_ENV_HEADER)         String environment,
        @RestPath                                       String orgId,
        @RestQuery("admin_only")                        boolean adminOnly,
        @RestQuery("limit")                             int limit,
        @RestQuery("offset")                            int offset,
        @RestQuery("include_permissions")               boolean includePermissions,
        @RestQuery("status")                            String status
    );
}
