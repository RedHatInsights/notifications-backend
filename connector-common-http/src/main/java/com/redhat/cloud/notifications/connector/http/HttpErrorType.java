package com.redhat.cloud.notifications.connector.http;

public enum HttpErrorType {
    SOCKET_TIMEOUT,
    CONNECT_TIMEOUT,
    CONNECTION_REFUSED,
    HTTP_3XX,
    HTTP_4XX,
    HTTP_5XX,
    SSL_HANDSHAKE,
    UNKNOWN_HOST,
    UNSUPPORTED_SSL_MESSAGE
}
