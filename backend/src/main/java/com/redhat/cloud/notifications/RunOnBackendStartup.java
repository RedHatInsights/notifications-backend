package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.db.repositories.EndpointEventTypeRepository;
import com.redhat.cloud.notifications.security.SecurityLog;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

@Startup
public class RunOnBackendStartup {

    @Inject
    StartupUtils startupUtils;

    @Inject
    EndpointEventTypeRepository endpointEventTypeRepository;

    @PostConstruct
    void postConstruct() {
        SecurityLog.logLifecycle("STARTUP", "notifications_backend", "success", "Notifications backend starting");

        startupUtils.initAccessLogFilter();
        startupUtils.logGitProperties();
        startupUtils.logExternalServiceUrl("quarkus.rest-client.rbac-authentication.url");
        startupUtils.logExternalServiceUrl("quarkus.rest-client.sources.url");
        startupUtils.disableRestClientContextualErrors();

        endpointEventTypeRepository.migrateData();
    }

    @PreDestroy
    void preDestroy() {
        SecurityLog.logLifecycle("SHUTDOWN", "notifications_backend", "success", "Notifications backend shutting down gracefully");
    }
}
