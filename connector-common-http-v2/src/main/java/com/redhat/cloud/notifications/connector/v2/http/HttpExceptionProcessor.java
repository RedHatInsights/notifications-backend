package com.redhat.cloud.notifications.connector.v2.http;

import com.redhat.cloud.notifications.connector.v2.ExceptionProcessor;
import com.redhat.cloud.notifications.connector.v2.MessageContext;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static com.redhat.cloud.notifications.connector.v2.http.ExchangeProperty.HTTP_ERROR_TYPE;
import static com.redhat.cloud.notifications.connector.v2.http.ExchangeProperty.HTTP_STATUS_CODE;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.HTTP_3XX;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.HTTP_4XX;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.HTTP_5XX;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.SOCKET_TIMEOUT;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.SSL_HANDSHAKE;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.UNKNOWN_HOST;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.UNSUPPORTED_SSL_MESSAGE;
import static jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS;
import static org.jboss.logging.Logger.Level.ERROR;

@ApplicationScoped
public class HttpExceptionProcessor extends ExceptionProcessor {

    private static final String HTTP_ERROR_LOG_MSG = "Message sending failed [orgId=%s, historyId=%s, targetUrl=%s, statusCode=%d, responseBody=%s]";

    @Inject
    HttpConnectorConfig connectorConfig;

    @Override
    protected void process(Throwable t, MessageContext context) {
        if (t instanceof ClientWebApplicationException e) {
            manageReturnedStatusCode(context, e.getResponse().getStatus(), e.getResponse().readEntity(String.class));
        } else if (t instanceof SocketTimeoutException) {
            context.setProperty(HTTP_ERROR_TYPE, SOCKET_TIMEOUT);
        } else if (t instanceof SSLHandshakeException) {
            context.setProperty(HTTP_ERROR_TYPE, SSL_HANDSHAKE);
        } else if (t instanceof UnknownHostException) {
            context.setProperty(HTTP_ERROR_TYPE, UNKNOWN_HOST);
        } else if (t instanceof SSLException) {
            context.setProperty(HTTP_ERROR_TYPE, UNSUPPORTED_SSL_MESSAGE);
        } else {
            logDefault(t, context);
        }
    }

    private void manageReturnedStatusCode(MessageContext context, int statusCode, String responseBody) {
        context.setProperty(HTTP_STATUS_CODE, statusCode);
        if (statusCode >= 300 && statusCode < 400) {
            context.setProperty(HTTP_ERROR_TYPE, HTTP_3XX);
            logHttpError(connectorConfig.getServerErrorLogLevel(), statusCode, responseBody, context);
        } else if (statusCode >= 400 && statusCode < 500 && statusCode != TOO_MANY_REQUESTS.getStatusCode()) {
            context.setProperty(HTTP_ERROR_TYPE, HTTP_4XX);
            logHttpError(connectorConfig.getClientErrorLogLevel(), statusCode, responseBody, context);
        } else if (statusCode == TOO_MANY_REQUESTS.getStatusCode() || statusCode >= 500) {
            context.setProperty(HTTP_ERROR_TYPE, HTTP_5XX);
            logHttpError(connectorConfig.getServerErrorLogLevel(), statusCode, responseBody, context);
        } else {
            logHttpError(ERROR, statusCode, responseBody, context);
        }
    }

    private void logHttpError(Logger.Level level, int statusCode, String responseBody, MessageContext context) {
        Log.logf(
                level,
                HTTP_ERROR_LOG_MSG,
                getOrgId(context),
                getExchangeId(context),
                getTargetUrl(context),
                statusCode,
                responseBody
        );
    }
}
