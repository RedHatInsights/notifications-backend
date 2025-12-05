package com.redhat.cloud.notifications.connector.drawer.recipients.recipientsresolver;


import com.redhat.cloud.notifications.connector.drawer.model.DrawerUser;
import com.redhat.cloud.notifications.connector.drawer.recipients.pojo.RecipientsQuery;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.util.Set;

@RegisterRestClient(configKey = "recipients-resolver")
public interface RecipientsResolverService {

    @PUT
    @Path("/internal/recipients-resolver")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Set<DrawerUser> getRecipients(RecipientsQuery resolversQuery);
}
