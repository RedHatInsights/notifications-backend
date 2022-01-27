package com.redhat.cloud.notifications;

import io.quarkus.runtime.Startup;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

@Startup
public class RunOnEngineStartup {

    @Inject
    StartupUtils startupUtils;

    @PostConstruct
    void postConstruct() {
        startupUtils.initAccessLogFilter();
        startupUtils.logGitProperties();
        startupUtils.logExternalServiceUrl("rbac-s2s/mp-rest/url");
    }
}
