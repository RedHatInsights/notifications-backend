package com.redhat.cloud.notifications.events;

public enum HttpErrorType {
    SOCKET_TIMEOUT("a timeout happened while waiting for the HTTP server response"),
    CONNECT_TIMEOUT("a timeout happened while connecting to the HTTP server"),
    CONNECTION_REFUSED("the connection to the HTTP server was refused"),
    HTTP_4XX(""), // Message not used.
    HTTP_5XX("the HTTP server responded with an HTTP status"),
    SSL_HANDSHAKE("the validation of the HTTP server SSL/TLS certificate failed"),
    UNKNOWN_HOST("the IP address of the HTTP server could not be determined"),
    HTTP_3XX("the HTTP server returned a redirection status code"),
    UNSUPPORTED_SSL_MESSAGE("the HTTP server returned an unsupported or unrecognized SSL message");

    private final String message;

    HttpErrorType(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
