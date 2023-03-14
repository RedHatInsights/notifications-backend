package com.redhat.cloud.notifications.ephemeral;

import com.redhat.cloud.notifications.models.Environment;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.flywaydb.core.Flyway;
import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import static javax.enterprise.inject.spi.ObserverMethod.DEFAULT_PRIORITY;

@ApplicationScoped
public class EphemeralDataInitializer {
    public static final int FLYWAY_PRIORITY = DEFAULT_PRIORITY;

    @Inject
    Environment environment;

    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    String datasourceUrl;

    @ConfigProperty(name = "quarkus.datasource.username")
    String datasourceUsername;

    @ConfigProperty(name = "quarkus.datasource.password")
    String datasourcePassword;

    public void runFlywayMigration(@Observes @Priority(FLYWAY_PRIORITY) StartupEvent event) {
        if (environment.isEphemeral()) {
            Log.warn("Starting Flyway from engine, for ephemeral only ... !");
            Flyway flyway = Flyway.configure().dataSource(datasourceUrl.replace("otel:", ""), datasourceUsername, datasourcePassword).load();
            flyway.migrate();
        }
    }
}
