package com.redhat.cloud.notifications;

import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import java.util.Optional;

@ApplicationScoped
public class ClowderConfigInitializer {

    private static final Logger LOGGER = Logger.getLogger(ClowderConfigInitializer.class);
    private static final String RBAC_AUTHENTICATION_URL_KEY = "rbac-authentication/mp-rest/url";
    private static final String RBAC_S2S_URL_KEY = "rbac-s2s/mp-rest/url";
    private static final String EPHEMERAL_RBAC_AUTH_TIMEOUT = "10000";

    @ConfigProperty(name = "clowder.endpoints.rbac-service")
    Optional<String> rbacClowderEndpoint;

    void init(@Observes StartupEvent event) {
        if (rbacClowderEndpoint.isPresent()) {
            String rbacUrl = "http://" + rbacClowderEndpoint.get();
            LOGGER.infof("Overriding the RBAC URL with the config value from Clowder: %s", rbacUrl);
            System.setProperty(RBAC_AUTHENTICATION_URL_KEY, rbacUrl);
            System.setProperty(RBAC_S2S_URL_KEY, rbacUrl);
        }
        increaseRbacAuthTimeoutOnEphemeral();
    }

    void increaseRbacAuthTimeoutOnEphemeral() {
        String envName = System.getenv("ENV_NAME");
        if (envName != null && envName.startsWith("env-ephemeral")) {
            LOGGER.warnf("Ephemeral environment detected, activating RBAC auth timeout workaround. The new timeout is %sms. Remove this ASAP!", EPHEMERAL_RBAC_AUTH_TIMEOUT);
            System.setProperty("rbac-authentication/mp-rest/readTimeout", EPHEMERAL_RBAC_AUTH_TIMEOUT);
        }
    }
}
