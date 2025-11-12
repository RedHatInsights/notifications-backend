package com.redhat.cloud.notifications.connector.v2.http;

import com.redhat.cloud.notifications.connector.v2.ExceptionHandler;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.http.models.NotificationToConnectorHttp;
import com.redhat.cloud.notifications.connector.v2.models.HandledExceptionDetails;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

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
public class HttpExceptionHandler extends ExceptionHandler {

    private static final String HTTP_ERROR_LOG_MSG = "Message sending failed [orgId=%s, historyId=%s, targetUrl=%s, statusCode=%d, responseBody=%s]";

    @Inject
    HttpConnectorConfig connectorConfig;

    @Override
    protected HandledExceptionDetails process(Throwable t, IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        HandledHttpExceptionDetails details = new HandledHttpExceptionDetails();
        if (t instanceof ClientWebApplicationException e) {
            details = manageReturnedStatusCode(incomingCloudEvent, e.getResponse().getStatus(), e.getResponse().readEntity(String.class));
        } else if (t instanceof SocketTimeoutException) {
            details.httpErrorType = SOCKET_TIMEOUT;
        } else if (t instanceof SSLHandshakeException) {
            details.httpErrorType = SSL_HANDSHAKE;
        } else if (t instanceof UnknownHostException) {
            details.httpErrorType = UNKNOWN_HOST;
        } else if (t instanceof SSLException) {
            details.httpErrorType = UNSUPPORTED_SSL_MESSAGE;
        } else {
            logDefault(t, incomingCloudEvent);
        }
        NotificationToConnectorHttp notificationToConnector = incomingCloudEvent.getData().mapTo(NotificationToConnectorHttp.class);
        details.targetUrl = getTargetUrl(notificationToConnector);
        return details;
    }

    private HandledHttpExceptionDetails manageReturnedStatusCode(IncomingCloudEventMetadata<JsonObject> incomingCloudEvent, int statusCode, String responseBody) {
        HandledHttpExceptionDetails details = new HandledHttpExceptionDetails();
        details.httpStatusCode = statusCode;
        if (statusCode >= 300 && statusCode < 400) {
            details.httpErrorType = HTTP_3XX;
            logHttpError(connectorConfig.getServerErrorLogLevel(), statusCode, responseBody, incomingCloudEvent);
        } else if (statusCode >= 400 && statusCode < 500 && statusCode != TOO_MANY_REQUESTS.getStatusCode()) {
            details.httpErrorType = HTTP_4XX;
            logHttpError(connectorConfig.getClientErrorLogLevel(), statusCode, responseBody, incomingCloudEvent);
        } else if (statusCode == TOO_MANY_REQUESTS.getStatusCode() || statusCode >= 500) {
            details.httpErrorType = HTTP_5XX;
            logHttpError(connectorConfig.getServerErrorLogLevel(), statusCode, responseBody, incomingCloudEvent);
        } else {
            logHttpError(ERROR, statusCode, responseBody, incomingCloudEvent);
        }
        return details;
    }

    private void logHttpError(Logger.Level level, int statusCode, String responseBody, IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        NotificationToConnectorHttp notificationToConnector = incomingCloudEvent.getData().mapTo(NotificationToConnectorHttp.class);
        String targetUrl = getTargetUrl(notificationToConnector);
        Log.logf(
                level,
                HTTP_ERROR_LOG_MSG,
                notificationToConnector.getOrgId(),
                incomingCloudEvent.getId(),
                targetUrl,
                statusCode,
                responseBody
        );
    }

    private static String getTargetUrl(NotificationToConnectorHttp notificationToConnector) {
        String targetUrl = null;
        if (notificationToConnector.getEndpointProperties() != null) {
            targetUrl = notificationToConnector.getEndpointProperties().getTargetUrl();
        }
        return targetUrl;
    }
}
