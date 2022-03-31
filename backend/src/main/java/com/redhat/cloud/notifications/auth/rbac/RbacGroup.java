package com.redhat.cloud.notifications.auth.rbac;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.time.Duration;
import java.util.UUID;

@ApplicationScoped
public class RbacGroup {

    @Inject
    @RestClient
    RbacServer rbacServer;

    @ConfigProperty(name = "rbac.enabled", defaultValue = "true")
    Boolean isRbacEnabled;

    public boolean validate(UUID groupId, String rhIdentity) {
        if (isRbacEnabled) {
            try {
                Response response = rbacServer.getGroup(groupId, rhIdentity)
                        .await()
                        .atMost(Duration.ofSeconds(2L));
                return response.getStatus() == 200;
            } catch (ClientWebApplicationException errorException) {
                if (errorException.getResponse().getStatus() == 404) {
                    // This is fine, the group was not found
                    return false;
                }

                throw errorException;
            }
        }

        return true;
    }
}
