package com.redhat.cloud.notifications;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

@Startup
public class RunOnEngineStartup {

    @Inject
    StartupUtils startupUtils;

    @PostConstruct
    void postConstruct() {
        startupUtils.initAccessLogFilter();
        startupUtils.logGitProperties();
        startupUtils.logExternalServiceUrl("quarkus.rest-client.rbac-s2s.url");
    }
}
