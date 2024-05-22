package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.models.Event;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EmailActorsResolver {

    private static final String STAGE_ENVIRONMENT = "stage";

    @Inject
    EngineConfig engineConfig;

    /**
     * Determines which sender should be set in the email from the given event.
     * When sending emails we will use the sender for both the sender itself
     * and the default recipient —the one that appears in the "to" field—.
     * @param event the event to determine the sender and the default
     *              recipients from.
     * @return the sender that should be used for the given event.
     */
    public String getEmailSender(final Event event) {
        try {
            if (isOCMApp(event)) {
                return getOCMEmailSender(event);
            }
        } catch (Exception e) {
            Log.warnf(e, "Something went wrong while determining the email sender, falling back to default value: %s", engineConfig.getRhHccSender());
        }
        return engineConfig.getRhHccSender();
    }

    private String getOCMEmailSender(Event event) {
        if (STAGE_ENVIRONMENT.equals(event.getSourceEnvironment())) {
            return engineConfig.getRhOpenshiftSenderStage();
        } else {
            return engineConfig.getRhOpenshiftSenderProd();
        }
    }

    public static boolean isOCMApp(Event event) {
        String bundle = event.getEventType().getApplication().getBundle().getName();
        String application = event.getEventType().getApplication().getName();
        return "openshift".equals(bundle) && "cluster-manager".equals(application);
    }
}
