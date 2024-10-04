package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.db.repositories.EndpointEventTypeRepository;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

@Startup
public class RunOnBackendStartup {

    @Inject
    StartupUtils startupUtils;

    @Inject
    EndpointEventTypeRepository endpointEventTypeRepository;

    @PostConstruct
    void postConstruct() {
        startupUtils.initAccessLogFilter();
        startupUtils.logGitProperties();
        startupUtils.logExternalServiceUrl("quarkus.rest-client.rbac-authentication.url");
        startupUtils.logExternalServiceUrl("quarkus.rest-client.sources.url");
        startupUtils.disableRestClientContextualErrors();

        endpointEventTypeRepository.migrateData();
    }
}
