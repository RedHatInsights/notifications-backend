package com.redhat.cloud.notifications.connector.http;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.jboss.logging.Logger;

import java.io.IOException;

import static com.redhat.cloud.notifications.connector.http.ExchangeProperty.HTTP_CLIENT_ERROR;
import static com.redhat.cloud.notifications.connector.http.ExchangeProperty.HTTP_SERVER_ERROR;
import static com.redhat.cloud.notifications.connector.http.ExchangeProperty.HTTP_STATUS_CODE;
import static org.apache.http.HttpStatus.SC_TOO_MANY_REQUESTS;
import static org.jboss.logging.Logger.Level.DEBUG;
import static org.jboss.logging.Logger.Level.ERROR;

@ApplicationScoped
public class HttpExceptionProcessor extends ExceptionProcessor {

    private static final String HTTP_ERROR_LOG_MSG = "Message sending failed [routeId=%s, orgId=%s, historyId=%s, targetUrl=%s, statusCode=%d, responseBody=%s]";

    @Inject
    HttpConnectorConfig connectorConfig;

    @Override
    protected void process(Throwable t, Exchange exchange) {
        if (t instanceof HttpOperationFailedException e) {
            exchange.setProperty(HTTP_STATUS_CODE, e.getStatusCode());
            if (e.getStatusCode() >= 400 && e.getStatusCode() < 500 && e.getStatusCode() != SC_TOO_MANY_REQUESTS) {
                if (connectorConfig.isDisableFaultyEndpoints()) {
                    exchange.setProperty(HTTP_CLIENT_ERROR, true);
                }
                logHttpError(DEBUG, e, exchange);
            } else if (e.getStatusCode() == SC_TOO_MANY_REQUESTS || e.getStatusCode() >= 500) {
                if (connectorConfig.isDisableFaultyEndpoints()) {
                    exchange.setProperty(HTTP_SERVER_ERROR, true);
                }
                logHttpError(DEBUG, e, exchange);
            } else {
                logHttpError(ERROR, e, exchange);
            }
        } else if (t instanceof IOException e) {
            if (connectorConfig.isDisableFaultyEndpoints()) {
                exchange.setProperty(HTTP_SERVER_ERROR, true);
            }
            logDefault(t, exchange);
        } else {
            logDefault(t, exchange);
        }
    }

    private void logHttpError(Logger.Level level, HttpOperationFailedException e, Exchange exchange) {
        Log.logf(
                level,
                HTTP_ERROR_LOG_MSG,
                getRouteId(exchange),
                getOrgId(exchange),
                getExchangeId(exchange),
                getTargetUrl(exchange),
                e.getStatusCode(),
                e.getResponseBody()
        );
    }
}
