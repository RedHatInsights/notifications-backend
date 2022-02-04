package com.redhat.cloud.notifications.ephemeral;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.InputStream;

import static com.redhat.cloud.notifications.db.FlywayWorkaround.FLYWAY_PRIORITY;
import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
public class EphemeralDataInitializer {

    public static final String EPHEMERAL_DATA_KEY = "NOTIFICATIONS_EPHEMERAL_DATA";

    private static final Logger LOGGER = Logger.getLogger(EphemeralDataInitializer.class);
    private static final String ENV_NAME_KEY = "ENV_NAME";
    private static final String ENV_EPHEMERAL_PREFIX = "env-ephemeral";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    /**
     * This method will always be invoked after the Flyway DB migration is complete.
     */
    void init(@Observes @Priority(FLYWAY_PRIORITY + 1) StartupEvent event) {
        String envName = System.getenv(ENV_NAME_KEY);
        if (envName != null && envName.startsWith(ENV_EPHEMERAL_PREFIX)) {
            loadFromFile();
            loadFromEnvVar();
        }
    }

    private void loadFromFile() {
        try {
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("/ephemeral/ephemeral_data.json")) {
                if (inputStream != null) {
                    String rawData = new String(inputStream.readAllBytes(), UTF_8);
                    if (!rawData.isBlank()) {
                        LOGGER.info("Loading ephemeral data from file");
                        EphemeralData data = objectMapper.readValue(rawData, EphemeralData.class);
                        persist(data);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Ephemeral data loading from file failed", e);
        }
    }

    private void loadFromEnvVar() {
        String rawData = System.getenv(EPHEMERAL_DATA_KEY);
        if (rawData != null && !rawData.isBlank()) {
            LOGGER.info("Loading ephemeral data from environment variable");
            try {
                EphemeralData data = objectMapper.readValue(rawData, EphemeralData.class);
                persist(data);
            } catch (Exception e) {
                LOGGER.error("Ephemeral data loading from environment variable failed", e);
            }
        }
    }

    private void persist(EphemeralData data) {
        if (data.bundles != null) {
            sessionFactory.withStatelessTransaction((session, transaction) -> {
                return Multi.createFrom().iterable(data.bundles)
                        .onItem().transformToUniAndConcatenate(b -> {
                            String selectBundleQuery = "FROM Bundle WHERE name = :bundleName";
                            return session.createQuery(selectBundleQuery, Bundle.class)
                                    .setParameter("bundleName", b.name)
                                    .getSingleResultOrNull()
                                    .onItem().ifNotNull().invoke(() ->
                                            LOGGER.infof("Bundle with name '%s' already exists, it won't be created from the ephemeral data", b.name)
                                    )
                                    .onItem().ifNull().switchTo(() -> {
                                        Bundle bundle = new Bundle();
                                        bundle.setName(b.name);
                                        bundle.setDisplayName(b.displayName);
                                        bundle.prePersist();
                                        return session.insert(bundle)
                                                .replaceWith(bundle);
                                    })
                                    .call(bundle -> {
                                        if (b.applications == null) {
                                            return Uni.createFrom().voidItem();
                                        } else {
                                            return Multi.createFrom().iterable(b.applications)
                                                    .onItem().transformToUniAndConcatenate(a -> {
                                                        String selectAppQuery = "FROM Application WHERE bundle.name = :bundleName AND name = :appName";
                                                        return session.createQuery(selectAppQuery, Application.class)
                                                                .setParameter("bundleName", b.name)
                                                                .setParameter("appName", a.name)
                                                                .getSingleResultOrNull()
                                                                .onItem().ifNotNull().invoke(() ->
                                                                        LOGGER.infof("Application with name '%s' already exists, it won't be created from the ephemeral data", a.name)
                                                                )
                                                                .onItem().ifNull().switchTo(() -> {
                                                                    Application application = new Application();
                                                                    application.setName(a.name);
                                                                    application.setDisplayName(a.displayName);
                                                                    application.setBundle(bundle);
                                                                    application.prePersist();
                                                                    return session.insert(application)
                                                                            .replaceWith(application);
                                                                })
                                                                .call(application -> {
                                                                    if (a.eventTypes == null) {
                                                                        return Uni.createFrom().voidItem();
                                                                    } else {
                                                                        return Multi.createFrom().iterable(a.eventTypes)
                                                                                .onItem().transformToUniAndConcatenate(et -> {
                                                                                    String selectEventTypeQuery = "FROM EventType WHERE application.bundle.name = :bundleName AND application.name = :appName AND name = :eventTypeName";
                                                                                    return session.createQuery(selectEventTypeQuery, EventType.class)
                                                                                            .setParameter("bundleName", b.name)
                                                                                            .setParameter("appName", a.name)
                                                                                            .setParameter("eventTypeName", et.name)
                                                                                            .getSingleResultOrNull()
                                                                                            .onItem().ifNotNull().invoke(() ->
                                                                                                    LOGGER.infof("Event type with name '%s' already exists, it won't be created from the ephemeral data", et.name)
                                                                                            )
                                                                                            .onItem().ifNull().switchTo(() -> {
                                                                                                EventType eventType = new EventType();
                                                                                                eventType.setName(et.name);
                                                                                                eventType.setDisplayName(et.displayName);
                                                                                                eventType.setDescription(et.description);
                                                                                                eventType.setApplication(application);
                                                                                                return session.insert(eventType)
                                                                                                        .replaceWith(eventType);
                                                                                            });
                                                                                })
                                                                                .onItem().ignoreAsUni();
                                                                    }
                                                                });
                                                    })
                                                    .onItem().ignoreAsUni();
                                        }
                                    });
                        })
                        .onItem().ignoreAsUni();
            }).await().indefinitely();
        }
    }
}
