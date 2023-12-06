package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.templates.DrawerTemplateMigrationService;
import com.redhat.cloud.notifications.templates.EmailTemplateMigrationService;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.util.List;

@Startup
public class RunOnEngineStartup {

    @Inject
    StartupUtils startupUtils;

    @Inject
    EmailTemplateMigrationService emailTemplateMigrationService;

    @Inject
    DrawerTemplateMigrationService drawerTemplateMigrationService;

    @PostConstruct
    void postConstruct() {
        startupUtils.initAccessLogFilter();
        startupUtils.logGitProperties();
        startupUtils.logExternalServiceUrl("quarkus.rest-client.export-service.url");

        List<String> warnings = emailTemplateMigrationService.migrate();
        if (!warnings.isEmpty()) {
            Log.warn("Email template migration ended with warnings, please check the logs for more details");
            warnings.stream().forEach(t -> Log.info(t));
        }

        warnings = drawerTemplateMigrationService.migrate();
        if (!warnings.isEmpty()) {
            Log.warn("Drawer template migration ended with warnings, please check the logs for more details");
            warnings.stream().forEach(t -> Log.info(t));
        }
    }
}
