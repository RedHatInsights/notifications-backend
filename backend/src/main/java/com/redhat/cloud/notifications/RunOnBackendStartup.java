package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.db.repositories.EndpointEventTypeRepository;
import io.quarkus.logging.Log;
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
        // Startup - SEC-MON-REQ-1 compliance (EOI-5 process_status)
        Log.infof("[action: STARTUP][resource_type: notifications_backend][principal: system][outcome: success] Notifications backend starting");

        startupUtils.initAccessLogFilter();
        startupUtils.logGitProperties();
        startupUtils.logExternalServiceUrl("quarkus.rest-client.rbac-authentication.url");
        startupUtils.logExternalServiceUrl("quarkus.rest-client.sources.url");
        startupUtils.disableRestClientContextualErrors();

        endpointEventTypeRepository.migrateData();
    }

    @PreDestroy
    void preDestroy() {
        // Graceful shutdown - SEC-MON-REQ-1 compliance (EOI-5 process_status)
        Log.infof("[action: SHUTDOWN][resource_type: notifications_backend][principal: system][outcome: success] Notifications backend shutting down gracefully");
    }
}
