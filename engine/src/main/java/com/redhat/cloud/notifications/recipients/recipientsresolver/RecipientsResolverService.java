package com.redhat.cloud.notifications.recipients.recipientsresolver;


import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.recipientsresolver.pojo.RecipientsQuery;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.util.Set;

@RegisterRestClient(configKey = "recipients-resolver")
public interface RecipientsResolverService {

    @PUT
    @Path("/internal/recipients-resolver")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Retry
    Set<User> getRecipients(RecipientsQuery resolversQuery);
}
