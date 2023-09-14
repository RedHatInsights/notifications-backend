package com.redhat.cloud.notifications.connector.google.chat;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpOperationFailedException;

import static org.jboss.logging.Logger.Level;
import static org.jboss.logging.Logger.Level.DEBUG;
import static org.jboss.logging.Logger.Level.ERROR;

@ApplicationScoped
public class GoogleChatExceptionProcessor extends ExceptionProcessor {

    private static final String HTTP_LOG_MSG = "Message sending failed on %s: [orgId=%s, historyId=%s, webhookUrl=%s] " +
            "with status code [%d] and body [%s]";

    @Override
    protected void process(Throwable t, Exchange exchange) {
        if (t instanceof HttpOperationFailedException e) {
            if (e.getStatusCode() >= 400 && e.getStatusCode() < 500) {
                // TODO Disable the integration using the 'integration-disabled' event type.
                log(DEBUG, e, exchange);
            } else {
                log(ERROR, e, exchange);
            }
        } else {
            logDefault(t, exchange);
        }
    }

    private void log(Level level, HttpOperationFailedException e, Exchange exchange) {
        Log.logf(
                level,
                HTTP_LOG_MSG,
                getRouteId(exchange),
                getOrgId(exchange),
                getExchangeId(exchange),
                getTargetUrl(exchange),
                e.getStatusCode(),
                e.getResponseBody()
        );
    }
}
