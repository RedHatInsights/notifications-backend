package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.EngineConfig;
import com.redhat.cloud.notifications.models.Event;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EmailActorsResolver {
    public static final String RH_INSIGHTS_SENDER = "\"Red Hat Insights\" noreply@redhat.com";
    /**
     * Standard "Red Hat Hybrid Cloud Console" sender that the vast majority of the
     * ConsoleDot applications will use.
     */
    public static final String RH_HCC_SENDER = "\"Red Hat Hybrid Cloud Console\" noreply@redhat.com";
    public static final String OPENSHIFT_SENDER_STAGE = "\"Red Hat OpenShift (staging)\" no-reply@openshift.com";
    public static final String OPENSHIFT_SENDER_PROD = "\"Red Hat OpenShift\" no-reply@openshift.com";

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
            String bundle = event.getEventType().getApplication().getBundle().getName();
            String application = event.getEventType().getApplication().getName();
            if ("openshift".equals(bundle) && "cluster-manager".equals(application)) {
                if (STAGE_ENVIRONMENT.equals(event.getSourceEnvironment())) {
                    return OPENSHIFT_SENDER_STAGE;
                } else {
                    return OPENSHIFT_SENDER_PROD;
                }
            } else {
                return getDefaultEmailSender();
            }
        } catch (Exception e) {
            String emailSender = getDefaultEmailSender();
            Log.warnf(e, "Something went wrong while determining the email sender, falling back to default value: %s", emailSender);
            return emailSender;
        }
    }

    private String getDefaultEmailSender() {
        if (engineConfig.isHccEmailSenderNameEnabled()) {
            return RH_HCC_SENDER;
        } else {
            return RH_INSIGHTS_SENDER;
        }
    }
}
