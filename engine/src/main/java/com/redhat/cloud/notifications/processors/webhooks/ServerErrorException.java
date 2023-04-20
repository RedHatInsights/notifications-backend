package com.redhat.cloud.notifications.processors.webhooks;

/**
 * This exception is thrown when the response of a webhook call contains a 5xx HTTP status.
 * Such status can be caused by a temporary remote issue so we should retry the call.
 */
public class ServerErrorException extends RuntimeException {

    public ServerErrorException() {
    }
}
