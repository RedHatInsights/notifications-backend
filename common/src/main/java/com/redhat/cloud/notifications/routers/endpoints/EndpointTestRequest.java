package com.redhat.cloud.notifications.routers.endpoints;

/**
 * Represents the structure of the "test endpoint" request that the user might
 * send.
 */
public class EndpointTestRequest {
    public String message;

    public EndpointTestRequest(final String message) {
        this.message = message;
    }
}
