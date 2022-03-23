package com.redhat.cloud.notifications;

import io.quarkus.runtime.Startup;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

@Startup
public class RunOnBackendStartup {

    @Inject
    StartupUtils startupUtils;

    @PostConstruct
    void postConstruct() {
        startupUtils.initAccessLogFilter();
        startupUtils.logGitProperties();
        startupUtils.logExternalServiceUrl("quarkus.rest-client.rbac-authentication.url");
        startupUtils.disableRestClientContextualErrors();
    }
}
