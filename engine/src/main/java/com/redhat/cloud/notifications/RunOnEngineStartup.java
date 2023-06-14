package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.templates.EmailTemplateMigrationService;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.List;

@Startup
public class RunOnEngineStartup {

    @Inject
    StartupUtils startupUtils;

    @Inject
    EmailTemplateMigrationService emailTemplateMigrationService;

    @PostConstruct
    void postConstruct() {
        startupUtils.initAccessLogFilter();
        startupUtils.logGitProperties();
        startupUtils.logExternalServiceUrl("quarkus.rest-client.rbac-s2s.url");
        startupUtils.logExternalServiceUrl("quarkus.rest-client.export-service.url");

        List<String> warnings = emailTemplateMigrationService.migrate();
        if (!warnings.isEmpty()) {
            Log.warn("Email template migration ended with warnings, please check the logs for more details");
            warnings.stream().forEach(t -> Log.info(t));
        }
    }
}
