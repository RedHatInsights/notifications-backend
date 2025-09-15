package com.redhat.cloud.notifications.connector.http;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static com.redhat.cloud.notifications.connector.http.HttpErrorType.CONNECTION_REFUSED;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.HTTP_3XX;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.HTTP_4XX;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.HTTP_5XX;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.SOCKET_TIMEOUT;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.SSL_HANDSHAKE;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.UNKNOWN_HOST;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.UNSUPPORTED_SSL_MESSAGE;
import static org.apache.http.HttpStatus.SC_TOO_MANY_REQUESTS;
import static org.jboss.logging.Logger.Level.ERROR;

/**
 * HTTP-specific exception processor that extends the base exception processor
 * to handle HTTP-specific errors and set appropriate error types.
 * This is the new version that replaces the Camel-based HttpExceptionProcessor.
 */
@ApplicationScoped
public class HttpExceptionProcessor extends ExceptionProcessor {

    private static final String HTTP_ERROR_LOG_MSG = "Message sending failed [routeId=%s, orgId=%s, historyId=%s, targetUrl=%s, statusCode=%d, responseBody=%s]";

    @Inject
    HttpConnectorConfig connectorConfig;

    @Override
    protected void process(Throwable t, ProcessingContext context) {
        if (t instanceof ClientWebApplicationException e) {
            String responseBody = null;
            try {
                responseBody = e.getResponse().readEntity(String.class);
            } catch (Exception ex) {
                responseBody = "Unable to read response body";
            }
            manageReturnedStatusCode(context, e.getResponse().getStatus(), responseBody);
        } else if (t instanceof ConnectException) {
            context.setAdditionalProperty("HTTP_ERROR_TYPE", CONNECTION_REFUSED);
        } else if (t instanceof SocketTimeoutException) {
            context.setAdditionalProperty("HTTP_ERROR_TYPE", SOCKET_TIMEOUT);
        } else if (t instanceof SSLHandshakeException) {
            context.setAdditionalProperty("HTTP_ERROR_TYPE", SSL_HANDSHAKE);
        } else if (t instanceof UnknownHostException) {
            context.setAdditionalProperty("HTTP_ERROR_TYPE", UNKNOWN_HOST);
        } else if (t instanceof SSLException) {
            context.setAdditionalProperty("HTTP_ERROR_TYPE", UNSUPPORTED_SSL_MESSAGE);
        } else {
            // Handle other HTTP-related exceptions
            handleGenericHttpException(t, context);
        }
    }

    private void manageReturnedStatusCode(ProcessingContext context, int statusCode, String responseBody) {
        context.setAdditionalProperty("HTTP_STATUS_CODE", statusCode);

        if (statusCode >= 300 && statusCode < 400) {
            context.setAdditionalProperty("HTTP_ERROR_TYPE", HTTP_3XX);
            logHttpError(connectorConfig.getServerErrorLogLevel(), statusCode, responseBody, context);
        } else if (statusCode >= 400 && statusCode < 500 && statusCode != SC_TOO_MANY_REQUESTS) {
            context.setAdditionalProperty("HTTP_ERROR_TYPE", HTTP_4XX);
            logHttpError(connectorConfig.getClientErrorLogLevel(), statusCode, responseBody, context);
        } else if (statusCode == SC_TOO_MANY_REQUESTS || statusCode >= 500) {
            context.setAdditionalProperty("HTTP_ERROR_TYPE", HTTP_5XX);
            logHttpError(connectorConfig.getServerErrorLogLevel(), statusCode, responseBody, context);
        } else {
            logHttpError(ERROR, statusCode, responseBody, context);
        }
    }

    private void logHttpError(Logger.Level level, int statusCode, String responseBody, ProcessingContext context) {
        Log.logf(
                level,
                HTTP_ERROR_LOG_MSG,
                context.getRouteId(),
                context.getOrgId(),
                context.getId(),
                context.getTargetUrl(),
                statusCode,
                responseBody
        );
    }

    private void handleGenericHttpException(Throwable t, ProcessingContext context) {
        // Check if it's a wrapped HTTP exception or other HTTP-related error
        Throwable cause = t.getCause();
        if (cause instanceof ClientWebApplicationException e) {
            String responseBody = null;
            try {
                responseBody = e.getResponse().readEntity(String.class);
            } catch (Exception ex) {
                responseBody = "Unable to read response body";
            }
            manageReturnedStatusCode(context, e.getResponse().getStatus(), responseBody);
        } else {
            // Fall back to default logging
            logDefault(t, context);
        }
    }
}
