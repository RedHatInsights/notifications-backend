package com.redhat.cloud.notifications.connector.http;

public enum HttpErrorType {
    CONNECT_TIMEOUT,
    CONNECTION_REFUSED,
    HTTP_4XX,
    HTTP_5XX,
    SSL_HANDSHAKE,
    UNKNOWN_HOST
}
