package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.models.Event;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EmailActorsResolver {
    /**
     * Standard "Red Hat Insights" sender that the vast majority of the
     * ConsoleDot applications will use.
     */
    public static final String RH_INSIGHTS_SENDER = "\"Red Hat Insights\" noreply@redhat.com";
    public static final String OPENSHIFT_SENDER = "no-reply@openshift.com";

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
                return OPENSHIFT_SENDER;
            } else {
                return RH_INSIGHTS_SENDER;
            }
        } catch (Exception e) {
            Log.warnf(e, "Something went wrong while determining the email sender, falling back to default value: %s", RH_INSIGHTS_SENDER);
            return RH_INSIGHTS_SENDER;
        }
    }
}
