package com.redhat.cloud.notifications.auth.rbac;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

import java.util.UUID;

@ApplicationScoped
public class RbacGroupValidator {

    @Inject
    @RestClient
    RbacServer rbacServer;

    @ConfigProperty(name = "rbac.enabled", defaultValue = "true")
    boolean isRbacEnabled;

    public boolean validate(UUID groupId, String rhIdentity) {
        if (isRbacEnabled) {
            try {
                Response response = rbacServer.getGroup(groupId, rhIdentity);
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
