package com.redhat.cloud.notifications.recipients.rest;

import com.redhat.cloud.notifications.recipients.model.User;
import com.redhat.cloud.notifications.recipients.resolver.RecipientsResolver;
import com.redhat.cloud.notifications.recipients.rest.pojo.RecipientsQuery;
import io.grpc.StatusRuntimeException;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import java.util.Set;


import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/internal/recipients-resolver")
public class RecipientsResolverResource {

    @Inject
    RecipientsResolver recipientsResolver;

    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Set<User> getRecipients(@NotNull @Valid RecipientsQuery recipientsQuery) {
        try {
            return recipientsResolver.findRecipients(
                recipientsQuery.orgId,
                recipientsQuery.recipientSettings,
                recipientsQuery.subscribers,
                recipientsQuery.unsubscribers,
                recipientsQuery.subscribedByDefault,
                recipientsQuery.recipientsAuthorizationCriterion);
        } catch (StatusRuntimeException e) {
            throw new WebApplicationException(String.format("Kessel error: %s", e.getMessage()));
        } catch (Exception e) {
            throw new WebApplicationException(e.getMessage());
        }
    }
}
