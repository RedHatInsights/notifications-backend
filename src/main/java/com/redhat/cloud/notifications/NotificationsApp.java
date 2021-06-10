package com.redhat.cloud.notifications;

import io.quarkus.runtime.StartupEvent;
import org.jboss.logging.Logger;

import javax.enterprise.event.Observes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationsApp {

    private static final String BUILD_COMMIT_ENV_NAME = "OPENSHIFT_BUILD_COMMIT";
    private static final String BUILD_REFERENCE_ENV_NAME = "OPENSHIFT_BUILD_REFERENCE";
    private static final String BUILD_NAME_ENV_NAME = "OPENSHIFT_BUILD_NAME";

    public static final String FILTER_REGEX = ".*(/health|/health/live|health/ready|/metrics) HTTP/[0-9].[0-9]\\\" 200.*";
    private static final Pattern pattern = Pattern.compile(FILTER_REGEX);

    private static final Logger LOG = Logger.getLogger(NotificationsApp.class);

    // we do need a event as parameter here, otherwise the init method won't get called.
    void init(@Observes StartupEvent ev) {
        filterAccessLogs();

        showVersionInfo();
    }

    private void filterAccessLogs() {
        java.util.logging.Logger accessLog = java.util.logging.Logger.getLogger("access_log");
        accessLog.setFilter(record -> {
            final String logMessage = record.getMessage().trim();
            Matcher matcher = pattern.matcher(logMessage);
            return !matcher.matches();
        });
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
