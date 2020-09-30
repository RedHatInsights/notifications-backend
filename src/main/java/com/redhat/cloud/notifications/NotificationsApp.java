package com.redhat.cloud.notifications;

import io.quarkus.runtime.StartupEvent;
import org.jboss.logging.Logger;

import javax.enterprise.event.Observes;

public class NotificationsApp  {
    private static final String BUILD_COMMIT_ENV_NAME = "OPENSHIFT_BUILD_COMMIT";
    private static final String BUILD_REFERENCE_ENV_NAME = "OPENSHIFT_BUILD_REFERENCE";
    private static final String BUILD_NAME_ENV_NAME = "OPENSHIFT_BUILD_NAME";

    private static final Logger LOG = Logger.getLogger(NotificationsApp.class);

    void init(@Observes StartupEvent ev) {
        showVersionInfo();
    }

    private void showVersionInfo() {
        LOG.info("Starting notifications backend");
        String buildCommit = System.getenv(BUILD_COMMIT_ENV_NAME);
        if (buildCommit != null) {
            String osBuildRef = System.getenv(BUILD_REFERENCE_ENV_NAME);
            String osBuildName = System.getenv(BUILD_NAME_ENV_NAME);

            LOG.infof("\ts2i-build [%s]\n\tfrom branch [%s]\n\twith git sha [%s]", osBuildName, osBuildRef, buildCommit);
        }
    }

}
