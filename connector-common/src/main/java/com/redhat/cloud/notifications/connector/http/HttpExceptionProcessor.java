package com.redhat.cloud.notifications.connector.http;

import io.quarkus.logging.Log;
import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.jboss.logging.Logger;
import java.io.IOException;
import java.time.temporal.ValueRange;

import static com.redhat.cloud.notifications.connector.ExceptionProcessor.getExchangeId;
import static com.redhat.cloud.notifications.connector.ExceptionProcessor.getOrgId;
import static com.redhat.cloud.notifications.connector.ExceptionProcessor.getRouteId;
import static com.redhat.cloud.notifications.connector.ExceptionProcessor.getTargetUrl;
import static com.redhat.cloud.notifications.connector.ExceptionProcessor.logDefault;
import static com.redhat.cloud.notifications.connector.http.Constants.DISABLE_ENDPOINT_CLIENT_ERRORS;
import static com.redhat.cloud.notifications.connector.http.Constants.HTTP_STATUS_CODE;
import static com.redhat.cloud.notifications.connector.http.Constants.INCREMENT_ENDPOINT_SERVER_ERRORS;
import static org.jboss.logging.Logger.Level.DEBUG;

public class HttpExceptionProcessor {

    private static final String HTTP_LOG_MSG = "Message sending failed on %s: [orgId=%s, historyId=%s, webhookUrl=%s] " +
        "with status code [%d] and body [%s]";

    static final ValueRange http400ErrorRange = ValueRange.of(400, 499);

    public static void process(Throwable t, Exchange exchange) {
        if (t instanceof HttpOperationFailedException e) {
            log(DEBUG, e, exchange);
            exchange.setProperty(HTTP_STATUS_CODE, e.getStatusCode());
            if (http400ErrorRange.isValidValue(e.getStatusCode())) {
                exchange.setProperty(DISABLE_ENDPOINT_CLIENT_ERRORS, true);
            } else {
                exchange.setProperty(INCREMENT_ENDPOINT_SERVER_ERRORS, true);
            }
        } else if (t instanceof IOException) {
            logDefault(t, exchange);
            exchange.setProperty(INCREMENT_ENDPOINT_SERVER_ERRORS, true);
        } else {
            logDefault(t, exchange);
        }
    }

    private static void log(Logger.Level level, HttpOperationFailedException e, Exchange exchange) {
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
