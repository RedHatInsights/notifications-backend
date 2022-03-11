package com.redhat.cloud.notifications;

import io.quarkus.runtime.Startup;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Startup
public class RunOnEngineStartup {

    private static Logger LOGGER = Logger.getLogger(RunOnEngineStartup.class);

    @Inject
    StartupUtils startupUtils;

    @PostConstruct
    void postConstruct() {
        startupUtils.initAccessLogFilter();
        startupUtils.logGitProperties();
        startupUtils.logExternalServiceUrl("rbac-s2s/mp-rest/url");

        logConfigValue("quarkus.http.ssl.certificate.key-store-file");
        logConfigValue("quarkus.http.ssl.certificate.key-store-file-type");

        try {
            String keystore = Files.readString(Paths.get(ConfigProvider.getConfig().getValue("quarkus.http.ssl.certificate.key-store-file", String.class)));
            LOGGER.info(keystore.substring(0, 5));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void logConfigValue(String configKey) {
        LOGGER.infof("%s=%s", configKey, ConfigProvider.getConfig().getValue(configKey, String.class));
    }
}
