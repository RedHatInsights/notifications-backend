package com.redhat.cloud.notifications.recipients.statup;

import com.redhat.cloud.notifications.recipients.config.RecipientsResolverConfig;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

@Startup
public class RunOnStartup {

    @Inject
    StartupUtils startupUtils;

    @Inject
    RecipientsResolverConfig recipientsResolverConfig;

    @PostConstruct
    void postConstruct() {
        startupUtils.initAccessLogFilter();
        startupUtils.logGitProperties();
        startupUtils.readKeystore(recipientsResolverConfig.getQuarkusItServiceKeystore(), recipientsResolverConfig.getQuarkusItServicePassword());
    }
}
