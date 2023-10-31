package com.redhat.cloud.notifications.recipients.statup;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

@Startup
public class RunOnStartup {

    @Inject
    StartupUtils startupUtils;

    @PostConstruct
    void postConstruct() {
        startupUtils.initAccessLogFilter();
        startupUtils.logGitProperties();
    }
}
