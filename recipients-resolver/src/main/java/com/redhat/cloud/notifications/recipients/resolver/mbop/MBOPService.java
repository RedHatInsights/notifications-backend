package com.redhat.cloud.notifications.recipients.resolver.mbop;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import java.util.List;

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
     * @param sortOrder sort order, either {@code asc} or {@code des}.
     * @param limit number of records to return per page. Defaults to 100.
     * @param offset the offset to apply to the requested records. Defaults
     *               to 0.
     * @return the list of requested users.
     */
    @Path("/v3/accounts/{orgId}/users")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<MBOPUser> getUsersByOrgId(
        @HeaderParam(Constants.MBOP_APITOKEN_HEADER)    String apiToken,
        @HeaderParam(Constants.MBOP_CLIENT_ID_HEADER)   String clientId,
        @HeaderParam(Constants.MBOP_ENV_HEADER)         String environment,
        @RestPath                                       String orgId,
        @RestQuery("admin_only")                        boolean adminOnly,
        @RestQuery("sortOrder")                         String sortOrder,
        @RestQuery("limit")                             int limit,
        @RestQuery("offset")                            int offset
    );
}
