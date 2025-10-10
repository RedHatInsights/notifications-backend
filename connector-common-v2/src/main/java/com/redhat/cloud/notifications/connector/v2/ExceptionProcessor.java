package com.redhat.cloud.notifications.connector.v2;

import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static com.redhat.cloud.notifications.connector.v2.CommonConstants.ORG_ID;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.TARGET_URL;

/**
 * Extend this class in an {@link ApplicationScoped} bean from a connector Maven module to change the
 * behavior of the connector in case of failure while calling an external service (e.g. Slack, Splunk...).
 * If this class is not extended, then the default implementation below will be used.
 */
@DefaultBean
@ApplicationScoped
public class ExceptionProcessor {

    private static final String DEFAULT_LOG_MSG = "Message sending failed: [orgId=%s, historyId=%s, targetUrl=%s]";

    @Inject
    OutgoingMessageSender outgoingMessageSender;

    public void processException(Throwable t, MessageContext originalMessage) {
        process(t, originalMessage);

        // Send failure response back to engine
        outgoingMessageSender.sendFailure(originalMessage, t.getMessage());
    }

    protected final void logDefault(Throwable t, MessageContext context) {
        Log.errorf(
                t,
                DEFAULT_LOG_MSG,
                getOrgId(context),
                getExchangeId(context),
                getTargetUrl(context)
        );
    }

    protected final String getExchangeId(MessageContext context) {
        return context.getIncomingCloudEventMetadata().getId();
    }

    protected final String getOrgId(MessageContext context) {
        return context.getProperty(ORG_ID, String.class);
    }

    protected final String getTargetUrl(MessageContext context) {
        return context.getProperty(TARGET_URL, String.class);
    }

    protected void process(Throwable t, MessageContext context) {
        logDefault(t, context);
    }
}
