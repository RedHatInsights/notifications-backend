package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.EngineConfig;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.Event;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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
    public static String OCM_PENDO_MESSAGE = "The email sender address will soon be changing from no-reply@openshift.com to <br/><b>noreply@redhat.com</b>.<br/><br/>If you have filtering or forwarding logic in place, you will need to update<br/>those rules by <b>%s</b>.";
    public static String GENERAL_PENDO_MESSAGE = "The email sender name will soon be changing from Red Hat Insights to<br/><b>Red Hat Hybrid Cloud Console</b>.<br/><br/>If you have filtering or forwarding logic in place, you will need to update<br/>those rules by <b>%s</b>.";
    public static String GENERAL_PENDO_TITLE = "Changes coming to email sender name";
    public static String OCM_PENDO_TITLE = "Changes coming to email sender address";
    static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("MMMM dd, uuuu");

    @Inject
    EngineConfig engineConfig;

    @ConfigProperty(name = "notifications.email.show.pendo.until.date", defaultValue = "2020-01-01")
    LocalDate showPendoUntil;

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
                if (STAGE_ENVIRONMENT.equals(event.getSourceEnvironment())) {
                    return OPENSHIFT_SENDER_STAGE;
                } else {
                    return OPENSHIFT_SENDER_PROD;
                }
            } else {
                return getDefaultEmailSender(event.getOrgId());
            }
        } catch (Exception e) {
            String emailSender = getDefaultEmailSender(event.getOrgId());
            Log.warnf(e, "Something went wrong while determining the email sender, falling back to default value: %s", emailSender);
            return emailSender;
        }
    }

    public EmailPendo getPendoEmailMessage(final Event event) {

        if (!isPendoMessageEnabled()) {
            return null;
        } else {
            String pendoMessage = GENERAL_PENDO_MESSAGE;
            String pendoTitle = GENERAL_PENDO_TITLE;
            try {
                if (isOCMApp(event)) {
                    pendoMessage = OCM_PENDO_MESSAGE;
                    pendoTitle = OCM_PENDO_TITLE;
                }
            } catch (Exception e) {
                Log.warnf(e, "Something went wrong while determining the email pendo message, falling back to default value: %s", pendoMessage);
            }
            return new EmailPendo(pendoTitle, addDateOnPendoMessage(pendoMessage));
        }
    }

    private boolean isOCMApp(Event event) {
        String bundle = event.getEventType().getApplication().getBundle().getName();
        String application = event.getEventType().getApplication().getName();
        return "openshift".equals(bundle) && "cluster-manager".equals(application);
    }

    private String getDefaultEmailSender(String orgId) {
        if (engineConfig.isHccEmailSenderNameEnabled(orgId)) {
            return RH_HCC_SENDER;
        } else {
            return RH_INSIGHTS_SENDER;
        }
    }

    public String addDateOnPendoMessage(String pendoPattern) {
        return String.format(pendoPattern, showPendoUntil.format(dateFormat));
    }

    private boolean isPendoMessageEnabled() {
        return LocalDate.now(ZoneId.of("UTC")).isBefore(showPendoUntil);
    }

    public void setShowPendoUntil(LocalDate showPendoUntil) {
        FeatureFlipper.checkTestLaunchMode();
        this.showPendoUntil = showPendoUntil;
    }
}
