package com.redhat.cloud.notifications.db;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ProfileManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.flywaydb.core.Flyway;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import java.util.List;

/**
 * This is a temporary workaround for a quarkus-flyway / quarkus-hibernate-reactive incompatibility.
 * See https://github.com/quarkusio/quarkus/issues/10716 for more details.
 */
@ApplicationScoped
public class FlywayWorkaround {

    private static final Logger LOGGER = Logger.getLogger(FlywayWorkaround.class);

    // The Flyway migration will only be run when the app is started with quarkus:dev or during the tests execution.
    private static final List<String> MIGRATION_PROFILES = List.of("dev", "test");

    @ConfigProperty(name = "quarkus.datasource.reactive.url")
    String datasourceUrl;

    @ConfigProperty(name = "quarkus.datasource.username")
    String datasourceUsername;

    @ConfigProperty(name = "quarkus.datasource.password")
    String datasourcePassword;

    public void runFlywayMigration(@Observes StartupEvent event) {
        if (MIGRATION_PROFILES.contains(ProfileManager.getActiveProfile())) {
            LOGGER.warn("Starting Flyway workaround... remove it ASAP!");
            Flyway flyway = Flyway.configure().dataSource("jdbc:" + datasourceUrl, datasourceUsername, datasourcePassword).load();
            flyway.migrate();
        }
    }
}
