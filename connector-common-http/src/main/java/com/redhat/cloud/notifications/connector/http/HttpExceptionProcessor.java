package com.redhat.cloud.notifications.connector.http;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.jboss.logging.Logger;

import javax.net.ssl.SSLHandshakeException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static com.redhat.cloud.notifications.connector.http.ExchangeProperty.HTTP_ERROR_TYPE;
import static com.redhat.cloud.notifications.connector.http.ExchangeProperty.HTTP_STATUS_CODE;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.CONNECTION_REFUSED;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.CONNECT_TIMEOUT;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.HTTP_4XX;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.HTTP_5XX;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.SSL_HANDSHAKE;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.UNKNOWN_HOST;
import static org.apache.http.HttpStatus.SC_TOO_MANY_REQUESTS;
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
                    exchange.setProperty(HTTP_ERROR_TYPE, HTTP_4XX);
                }
                logHttpError(connectorConfig.getClientErrorLogLevel(), e, exchange);
            } else if (e.getStatusCode() == SC_TOO_MANY_REQUESTS || e.getStatusCode() >= 500) {
                if (connectorConfig.isDisableFaultyEndpoints()) {
                    exchange.setProperty(HTTP_ERROR_TYPE, HTTP_5XX);
                }
                logHttpError(connectorConfig.getServerErrorLogLevel(), e, exchange);
            } else {
                logHttpError(ERROR, e, exchange);
            }
        } else if (t instanceof ConnectTimeoutException || t instanceof SocketTimeoutException) {
            if (connectorConfig.isDisableFaultyEndpoints()) {
                exchange.setProperty(HTTP_ERROR_TYPE, CONNECT_TIMEOUT);
            } else {
                logDefault(t, exchange);
            }
        } else if (t instanceof HttpHostConnectException) {
            if (connectorConfig.isDisableFaultyEndpoints()) {
                exchange.setProperty(HTTP_ERROR_TYPE, CONNECTION_REFUSED);
            } else {
                logDefault(t, exchange);
            }
        } else if (t instanceof SSLHandshakeException) {
            if (connectorConfig.isDisableFaultyEndpoints()) {
                exchange.setProperty(HTTP_ERROR_TYPE, SSL_HANDSHAKE);
            } else {
                logDefault(t, exchange);
            }
        } else if (t instanceof UnknownHostException) {
            if (connectorConfig.isDisableFaultyEndpoints()) {
                exchange.setProperty(HTTP_ERROR_TYPE, UNKNOWN_HOST);
            } else {
                logDefault(t, exchange);
            }
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
