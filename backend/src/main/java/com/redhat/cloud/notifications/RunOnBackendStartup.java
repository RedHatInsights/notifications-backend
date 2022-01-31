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
        startupUtils.logExternalServiceUrl("rbac-authentication/mp-rest/url");
        // TODO Uncomment when Quarkus is bumped to 2.6.4.Final or newer.
        //startupUtils.disableRestClientContextualErrors();
    }
}
