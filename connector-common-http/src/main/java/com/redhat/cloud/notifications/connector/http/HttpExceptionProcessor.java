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
import org.jboss.resteasy.reactive.ClientWebApplicationException;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static com.redhat.cloud.notifications.connector.http.ExchangeProperty.HTTP_ERROR_TYPE;
import static com.redhat.cloud.notifications.connector.http.ExchangeProperty.HTTP_STATUS_CODE;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.CONNECTION_REFUSED;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.CONNECT_TIMEOUT;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.HTTP_3XX;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.HTTP_4XX;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.HTTP_5XX;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.SOCKET_TIMEOUT;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.SSL_HANDSHAKE;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.UNKNOWN_HOST;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.UNSUPPORTED_SSL_MESSAGE;
import static org.apache.http.HttpStatus.SC_TOO_MANY_REQUESTS;
import static org.jboss.logging.Logger.Level.ERROR;

@ApplicationScoped
public class HttpExceptionProcessor extends ExceptionProcessor {

    private static final String HTTP_ERROR_LOG_MSG = "Message sending failed [routeId=%s, orgId=%s, historyId=%s, targetUrl=%s, statusCode=%d, responseBody=%s]";

    @Inject
    HttpConnectorConfig connectorConfig;

    @Override
    protected void process(Throwable t, Exchange exchange) {
        if (t instanceof ClientWebApplicationException e) {
            manageReturnedStatusCode(exchange, e.getResponse().getStatus(), e.getResponse().readEntity(String.class));
        } else if (t instanceof HttpOperationFailedException e) {
            manageReturnedStatusCode(exchange, e.getStatusCode(), e.getResponseBody());
        } else if (t instanceof ConnectTimeoutException) {
            exchange.setProperty(HTTP_ERROR_TYPE, CONNECT_TIMEOUT);
        } else if (t instanceof SocketTimeoutException) {
            exchange.setProperty(HTTP_ERROR_TYPE, SOCKET_TIMEOUT);
        } else if (t instanceof HttpHostConnectException) {
            exchange.setProperty(HTTP_ERROR_TYPE, CONNECTION_REFUSED);
        } else if (t instanceof SSLHandshakeException) {
            exchange.setProperty(HTTP_ERROR_TYPE, SSL_HANDSHAKE);
        } else if (t instanceof UnknownHostException) {
            exchange.setProperty(HTTP_ERROR_TYPE, UNKNOWN_HOST);
        } else if (t instanceof SSLException) {
            exchange.setProperty(HTTP_ERROR_TYPE, UNSUPPORTED_SSL_MESSAGE);
        } else {
            logDefault(t, exchange);
        }
    }

    private void manageReturnedStatusCode(Exchange exchange, int statusCode, String responseBody) {
        exchange.setProperty(HTTP_STATUS_CODE, statusCode);
        if (statusCode >= 300 && statusCode < 400) {
            exchange.setProperty(HTTP_ERROR_TYPE, HTTP_3XX);
            logHttpError(connectorConfig.getServerErrorLogLevel(), statusCode, responseBody, exchange);
        } else if (statusCode >= 400 && statusCode < 500 && statusCode != SC_TOO_MANY_REQUESTS) {
            exchange.setProperty(HTTP_ERROR_TYPE, HTTP_4XX);
            logHttpError(connectorConfig.getClientErrorLogLevel(), statusCode, responseBody, exchange);
        } else if (statusCode == SC_TOO_MANY_REQUESTS || statusCode >= 500) {
            exchange.setProperty(HTTP_ERROR_TYPE, HTTP_5XX);
            logHttpError(connectorConfig.getServerErrorLogLevel(), statusCode, responseBody, exchange);
        } else {
            logHttpError(ERROR, statusCode, responseBody, exchange);
        }
    }

    private void logHttpError(Logger.Level level, int statusCode, String responseBody, Exchange exchange) {
        Log.logf(
                level,
                HTTP_ERROR_LOG_MSG,
                getRouteId(exchange),
                getOrgId(exchange),
                getExchangeId(exchange),
                getTargetUrl(exchange),
                statusCode,
                responseBody
        );
    }
}
