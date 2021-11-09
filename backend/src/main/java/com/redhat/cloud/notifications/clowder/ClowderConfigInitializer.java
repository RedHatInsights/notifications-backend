package com.redhat.cloud.notifications.clowder;

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

    @ConfigProperty(name = "clowder.endpoints.rbac-service")
    Optional<String> rbacClowderEndpoint;

    void init(@Observes StartupEvent event) {
        if (rbacClowderEndpoint.isPresent()) {
            String rbacUrl = "http://" + rbacClowderEndpoint.get();
            LOGGER.infof("Overriding the RBAC URL with the config value from Clowder: %s", rbacUrl);
            System.setProperty(RBAC_AUTHENTICATION_URL_KEY, rbacUrl);
            System.setProperty(RBAC_S2S_URL_KEY, rbacUrl);
        }
    }
}
