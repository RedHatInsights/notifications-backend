package com.redhat.cloud.notifications.db;

import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.flywaydb.core.Flyway;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

/**
 * This is a temporary workaround for a quarkus-flyway / quarkus-hibernate-reactive incompatibility.
 * See https://github.com/quarkusio/quarkus/issues/10716 for more details.
 */
@ApplicationScoped
public class FlywayWorkaround {

    private static final Logger LOGGER = Logger.getLogger(FlywayWorkaround.class);

    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    String datasourceUrl;

    @ConfigProperty(name = "quarkus.datasource.username")
    String datasourceUsername;

    @ConfigProperty(name = "quarkus.datasource.password")
    String datasourcePassword;

    public void runFlywayMigration(@Observes StartupEvent event) {
        LOGGER.warn("Starting Flyway workaround... remove it ASAP!");
        Flyway flyway = Flyway.configure().dataSource(datasourceUrl, datasourceUsername, datasourcePassword).load();
        flyway.migrate();
    }
}
