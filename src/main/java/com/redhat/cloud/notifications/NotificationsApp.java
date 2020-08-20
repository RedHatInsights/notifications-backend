package com.redhat.cloud.notifications;

import io.vertx.ext.web.Router;

import javax.enterprise.event.Observes;
import javax.ws.rs.core.Application;
import java.util.logging.Logger;

//@ApplicationPath("/api/notifications/v1.0")
public class NotificationsApp extends Application {

    Logger log = Logger.getLogger("Notifications-backend");

    // Server init is done here, so we can do some more initialisation
    void observeRouter(@Observes Router router) {
        //Produce access log
        showVersionInfo();

    }

    private void showVersionInfo() {
        // Produce build-info and log on startup

        String commmitSha = System.getenv("OPENSHIFT_BUILD_COMMIT");
        if (commmitSha != null) {
            String openshift_build_reference = System.getenv("OPENSHIFT_BUILD_REFERENCE");
            String openshift_build_name = System.getenv("OPENSHIFT_BUILD_NAME");

            String info = String.format("\n    s2i-build [%s]\n    from branch [%s]\n    with git sha [%s]",
                    openshift_build_name,
                    openshift_build_reference,
                    commmitSha);
            log.info(info);
        } else {
            log.info("\n    Not built on OpenShift s2i, no version info available");
        }
    }

}
