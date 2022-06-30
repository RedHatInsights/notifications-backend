package com.redhat.cloud.notifications.ephemeral;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.transaction.Transactional;
import java.io.InputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
public class EphemeralDataInitializer {

    public static final String EPHEMERAL_DATA_KEY = "NOTIFICATIONS_EPHEMERAL_DATA";

    private static final String ENV_NAME_KEY = "ENV_NAME";
    private static final String ENV_EPHEMERAL_PREFIX = "env-ephemeral";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EntityManager entityManager;

    void init(@Observes StartupEvent event) {
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
                        Log.info("Loading ephemeral data from file");
                        EphemeralData data = objectMapper.readValue(rawData, EphemeralData.class);
                        persist(data);
                    }
                }
            }
        } catch (Exception e) {
            Log.error("Ephemeral data loading from file failed", e);
        }
    }

    private void loadFromEnvVar() {
        String rawData = System.getenv(EPHEMERAL_DATA_KEY);
        if (rawData != null && !rawData.isBlank()) {
            Log.info("Loading ephemeral data from environment variable");
            try {
                EphemeralData data = objectMapper.readValue(rawData, EphemeralData.class);
                persist(data);
            } catch (Exception e) {
                Log.error("Ephemeral data loading from environment variable failed", e);
            }
        }
    }

    @Transactional
    void persist(EphemeralData data) {
        if (data.bundles != null) {
            for (com.redhat.cloud.notifications.ephemeral.Bundle b : data.bundles) {
                String selectBundleQuery = "FROM Bundle WHERE name = :bundleName";
                Bundle bundle;
                try {
                    bundle = entityManager.createQuery(selectBundleQuery, Bundle.class)
                            .setParameter("bundleName", b.name)
                            .getSingleResult();
                    Log.infof("Bundle with name '%s' already exists, it won't be created from the ephemeral data", b.name);
                } catch (NoResultException e) {
                    bundle = new Bundle();
                    bundle.setName(b.name);
                    bundle.setDisplayName(b.displayName);
                    entityManager.persist(bundle);
                }

                if (b.applications != null) {
                    for (com.redhat.cloud.notifications.ephemeral.Application a : b.applications) {
                        String selectAppQuery = "FROM Application WHERE bundle.name = :bundleName AND name = :appName";
                        Application app;
                        try {
                            app = entityManager.createQuery(selectAppQuery, Application.class)
                                    .setParameter("bundleName", b.name)
                                    .setParameter("appName", a.name)
                                    .getSingleResult();
                            Log.infof("Application with name '%s' already exists, it won't be created from the ephemeral data", a.name);
                        } catch (NoResultException e) {
                            app = new Application();
                            app.setName(a.name);
                            app.setDisplayName(a.displayName);
                            app.setBundle(bundle);
                            app.setBundleId(bundle.getId());
                            entityManager.persist(app);
                        }

                        if (a.eventTypes != null) {
                            for (com.redhat.cloud.notifications.ephemeral.EventType et : a.eventTypes) {
                                String selectEventTypeQuery = "FROM EventType WHERE application.bundle.name = :bundleName AND application.name = :appName AND name = :eventTypeName";
                                EventType eventType;
                                try {
                                    entityManager.createQuery(selectEventTypeQuery, EventType.class)
                                            .setParameter("bundleName", b.name)
                                            .setParameter("appName", a.name)
                                            .setParameter("eventTypeName", et.name)
                                            .getSingleResult();
                                    Log.infof("Event type with name '%s' already exists, it won't be created from the ephemeral data", et.name);
                                } catch (NoResultException e) {
                                    eventType = new EventType();
                                    eventType.setName(et.name);
                                    eventType.setDisplayName(et.displayName);
                                    eventType.setDescription(et.description);
                                    eventType.setApplication(app);
                                    eventType.setApplicationId(app.getId());
                                    entityManager.persist(eventType);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
