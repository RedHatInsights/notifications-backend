package com.redhat.cloud.notifications.connector.google.chat;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.quarkus.logging.Log;
import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpOperationFailedException;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GoogleChatExceptionProcessor extends ExceptionProcessor {

    private static final String HTTP_LOG_MSG = "Message sending failed on %s: [orgId=%s, historyId=%s, webhookUrl=%s] " +
            "with status code [%d] and body [%s]";

    @Override
    protected void process(Throwable t, Exchange exchange) {
        if (t instanceof HttpOperationFailedException) {
            HttpOperationFailedException e = (HttpOperationFailedException) t;
            Log.errorf(
                    HTTP_LOG_MSG,
                    getRouteId(exchange),
                    getOrgId(exchange),
                    getExchangeId(exchange),
                    getTargetUrl(exchange),
                    e.getStatusCode(),
                    e.getResponseBody()
            );
        } else {
            logDefault(t, exchange);
        }
    }
}
