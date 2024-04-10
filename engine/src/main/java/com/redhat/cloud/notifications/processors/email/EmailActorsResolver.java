package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.models.Event;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@ApplicationScoped
public class EmailActorsResolver {
    @Deprecated
    public static final String RH_INSIGHTS_SENDER = "\"Red Hat Insights\" noreply@redhat.com";
    @Deprecated
    public static final String OPENSHIFT_SENDER_STAGE = "\"Red Hat OpenShift (staging)\" no-reply@openshift.com";
    @Deprecated
    public static final String OPENSHIFT_SENDER_PROD = "\"Red Hat OpenShift\" no-reply@openshift.com";
    private static final String STAGE_ENVIRONMENT = "stage";
    public static String OCM_PENDO_MESSAGE = "The email sender address will soon be changing from no-reply@openshift.com to <br/><b>noreply@redhat.com</b>.<br/><br/>If you have filtering or forwarding logic in place, you will need to update<br/>those rules by <b>%s</b>.";
    public static String GENERAL_PENDO_MESSAGE = "The email sender name will soon be changing from Red Hat Insights to<br/><b>Red Hat Hybrid Cloud Console</b>.<br/><br/>If you have filtering or forwarding logic in place, you will need to update<br/>those rules by <b>%s</b>.";
    public static String GENERAL_PENDO_TITLE = "Changes coming to email sender name";
    public static String OCM_PENDO_TITLE = "Changes coming to email sender address";
    static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("MMMM dd, uuuu");

    @ConfigProperty(name = "notifications.email.show.pendo.until.date", defaultValue = "2024-05-01")
    LocalDate emailChangesActivationDate;

    /**
     * The email sender address for the Red Hat Hybrid Cloud Console.
     */
    @ConfigProperty(name = "notifications.email.sender.hybrid.cloud.console", defaultValue = "\"Red Hat Hybrid Cloud Console\" noreply@redhat.com")
    protected String rhHccSender;

    /**
     * The email sender address for OpenShift in stage.
     */
    @ConfigProperty(name = "notifications.email.sender.openshift.stage", defaultValue = "\"Red Hat OpenShift (staging)\" noreply@redhat.com")
    protected String rhOpenshiftSenderStage;

    /**
     * The email sender address for OpenShift in production.
     */
    @ConfigProperty(name = "notifications.email.sender.openshift.prod", defaultValue = "\"Red Hat OpenShift\" noreply@redhat.com")
    protected String rhOpenshiftSenderProd;

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
            } else {
                return getDefaultEmailSender();
            }
        } catch (Exception e) {
            String emailSender = getDefaultEmailSender();
            Log.warnf(e, "Something went wrong while determining the email sender, falling back to default value: %s", emailSender);
            return emailSender;
        }
    }

    private String getOCMEmailSender(Event event) {
        if (isHccEmailSenderNameEnabled()) {
            if (STAGE_ENVIRONMENT.equals(event.getSourceEnvironment())) {
                return this.rhOpenshiftSenderStage;
            } else {
                return this.rhOpenshiftSenderProd;
            }
        } else {
            if (STAGE_ENVIRONMENT.equals(event.getSourceEnvironment())) {
                return OPENSHIFT_SENDER_STAGE;
            } else {
                return OPENSHIFT_SENDER_PROD;
            }
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

    private String getDefaultEmailSender() {
        if (isHccEmailSenderNameEnabled()) {
            return this.rhHccSender;
        } else {
            return RH_INSIGHTS_SENDER;
        }
    }

    public String addDateOnPendoMessage(String pendoPattern) {
        return String.format(pendoPattern, emailChangesActivationDate.format(dateFormat));
    }

    private boolean isPendoMessageEnabled() {
        return LocalDate.now(ZoneId.of("UTC")).isBefore(emailChangesActivationDate);
    }

    private boolean isHccEmailSenderNameEnabled() {
        return LocalDate.now(ZoneId.of("UTC")).isAfter(emailChangesActivationDate);
    }

    public void setEmailChangesActivationDate(LocalDate emailChangesActivationDate) {
        this.emailChangesActivationDate = emailChangesActivationDate;
    }

    public String getRhHccSender() {
        return this.rhHccSender;
    }

    public String getOpenshiftSenderStage() {
        return this.rhOpenshiftSenderStage;
    }

    public String getOpenshiftSenderProd() {
        return this.rhOpenshiftSenderProd;
    }
}
