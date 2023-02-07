package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.FeatureFlipper;
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

    @Inject
    FeatureFlipper featureFlipper;

    @PostConstruct
    void postConstruct() {
        startupUtils.initAccessLogFilter();
        startupUtils.logGitProperties();
        startupUtils.logExternalServiceUrl("quarkus.rest-client.rbac-s2s.url");
        if (featureFlipper.isUseTemplatesFromDb()) {
            List<String> warnings = emailTemplateMigrationService.migrate();
            warnings.stream().forEach(t -> Log.info(t));
        }
    }
}
